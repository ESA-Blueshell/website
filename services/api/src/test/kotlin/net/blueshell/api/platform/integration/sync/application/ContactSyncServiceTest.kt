package net.blueshell.api.platform.integration.sync.application

import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.platform.integration.contact.adapter.ContactData
import net.blueshell.api.platform.integration.contact.persistence.Contact
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactRepository
import net.blueshell.api.platform.integration.sync.port.ContactSyncTarget
import net.blueshell.api.platform.integration.sync.port.SyncTargetRegistry
import net.blueshell.api.platform.integration.sync.port.TargetSystem
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ContactSyncServiceTest {

    private val fanOut: SyncFanOut = mock()
    private val userService: UserService = mock()
    private val contactRepository: ContactRepository = mock()
    private val target: ContactSyncTarget = mock<ContactSyncTarget>().also {
        whenever(it.system).thenReturn(TargetSystem.LISTMONK)
    }
    private val registry: SyncTargetRegistry = mock<SyncTargetRegistry>().also {
        whenever(it.forContact()).thenReturn(listOf(target))
    }

    private val service = ContactSyncService(registry, fanOut, userService, contactRepository)

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
    fun `remove soft-deletes the contact and pushes null to every target`() {
        val contact = Contact(userId = userId).also { it.id = 99L }
        whenever(contactRepository.findByUserId(userId)).thenReturn(contact)
        doNothing().whenever(contactRepository).softDeleteById(99L)

        service.remove(userId)

        verify(contactRepository).softDeleteById(99L)
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
