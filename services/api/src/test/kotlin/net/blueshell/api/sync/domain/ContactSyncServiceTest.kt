package net.blueshell.api.sync.domain

import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import net.blueshell.api.contact.api.ContactData
import net.blueshell.api.shared.enums.TargetSystem
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ContactSyncServiceTest {

    private val fanOut: SyncFanOut = mock()
    private val userService: UserService = mock()
    private val target: ContactSyncTarget = mock<ContactSyncTarget>().also {
        whenever(it.system).thenReturn(TargetSystem.BREVO)
    }
    private val registry: SyncTargetRegistry = mock<SyncTargetRegistry>().also {
        whenever(it.forContact()).thenReturn(listOf(target))
    }

    private val service = ContactSyncService(registry, fanOut, userService)

    private val userId = 42L

    private fun stubUser(): User = mock<User>().also {
        whenever(it.email).thenReturn("a@b.c")
        whenever(it.firstName).thenReturn("A")
        whenever(it.lastName).thenReturn("B")
        whenever(it.phoneNumber).thenReturn(null)
        whenever(it.newsletter).thenReturn(false)
        whenever(it.roles).thenReturn(mutableSetOf())
        whenever(userService.findById(userId)).thenReturn(it)
    }

    @Test
    fun `sync loads the user and delegates one push per contact target`() {
        stubUser()

        service.sync(userId)

        val captor = argumentCaptor<ContactData>()
        verify(fanOut).push(
            eq("USER"),
            eq(userId),
            captor.capture(),
            eq(listOf(target)),
            any(),
        )
        val data = captor.firstValue
        assert(data.email == "a@b.c")
        assert(data.firstName == "A")
    }

    @Test
    fun `remove pushes null to every target`() {
        service.remove(userId)

        verify(fanOut).push(
            eq("USER"),
            eq(userId),
            eq(null),
            eq(listOf(target)),
            any(),
        )
    }

    @Test
    fun `sync is a no-op when the user does not exist`() {
        whenever(userService.findById(userId)).thenThrow(RuntimeException("not found"))

        service.sync(userId)

        verify(fanOut, never()).push<ContactData>(any(), any(), any(), any(), any())
    }
}
