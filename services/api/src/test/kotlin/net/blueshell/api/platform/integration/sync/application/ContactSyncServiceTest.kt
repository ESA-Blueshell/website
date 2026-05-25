package net.blueshell.api.platform.integration.sync.application

import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.platform.integration.contact.adapter.ContactData
import net.blueshell.api.platform.integration.contact.persistence.Contact
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactRepository
import net.blueshell.api.platform.integration.sync.persistence.ExternalIdMapping
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

    private val mappings: ExternalIdMappingService = mock()
    private val userService: UserService = mock()
    private val contactRepository: ContactRepository = mock()
    private val target: ContactSyncTarget = mock<ContactSyncTarget>().also {
        whenever(it.system).thenReturn(TargetSystem.LISTMONK)
    }
    private val registry: SyncTargetRegistry = mock<SyncTargetRegistry>().also {
        whenever(it.forContact()).thenReturn(listOf(target))
    }

    private val service = ContactSyncService(registry, mappings, userService, contactRepository)

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

    private fun existingMapping(externalId: String) = ExternalIdMapping("USER", userId, "LISTMONK", externalId)

    @Test
    fun `creates a new external id when none is stored`() {
        stubUser()
        whenever(mappings.find("USER", userId, "LISTMONK")).thenReturn(null)
        whenever(target.push(eq(userId), any<ContactData>(), eq(null))).thenReturn("99")
        whenever(contactRepository.findByUserId(userId)).thenReturn(null)
        whenever(contactRepository.save(any<Contact>())).thenAnswer { it.arguments[0] }

        service.sync(userId)

        verify(target).push(eq(userId), any<ContactData>(), eq(null))
        verify(mappings).upsert("USER", userId, "LISTMONK", "99")
    }

    @Test
    fun `updates with stored id and persists it back`() {
        stubUser()
        whenever(mappings.find("USER", userId, "LISTMONK")).thenReturn(existingMapping("77"))
        whenever(target.push(eq(userId), any<ContactData>(), eq("77"))).thenReturn("77")
        val contact = Contact(userId = userId).also { it.id = 99L }
        whenever(contactRepository.findByUserId(userId)).thenReturn(contact)
        whenever(contactRepository.save(any<Contact>())).thenReturn(contact)

        service.sync(userId)

        verify(target).push(eq(userId), any<ContactData>(), eq("77"))
        verify(mappings).upsert("USER", userId, "LISTMONK", "77")
    }

    @Test
    fun `remove soft-deletes the contact and pushes null to the target`() {
        val contact = Contact(userId = userId).also { it.id = 99L }
        whenever(contactRepository.findByUserId(userId)).thenReturn(contact)
        doNothing().whenever(contactRepository).softDeleteById(99L)
        whenever(mappings.find("USER", userId, "LISTMONK")).thenReturn(existingMapping("77"))
        whenever(target.push(eq(userId), eq(null), eq("77"))).thenReturn(null)
        whenever(contactRepository.save(any<Contact>())).thenAnswer { it.arguments[0] }

        service.remove(userId)

        verify(contactRepository).softDeleteById(99L)
        verify(target).push(eq(userId), eq(null), eq("77"))
        verify(mappings).upsert("USER", userId, "LISTMONK", null)
    }

    @Test
    fun `skip sync when the user does not exist`() {
        whenever(userService.findById(userId)).thenThrow(RuntimeException("not found"))

        service.sync(userId)

        verify(target, never()).push(any(), any(), any())
    }

    @Test
    fun `passes the user's current data to the target`() {
        stubUser()
        whenever(mappings.find("USER", userId, "LISTMONK")).thenReturn(null)
        whenever(target.push(any(), any<ContactData>(), any())).thenReturn("1")
        whenever(contactRepository.findByUserId(userId)).thenReturn(null)
        whenever(contactRepository.save(any<Contact>())).thenAnswer { it.arguments[0] }

        service.sync(userId)

        val captor = argumentCaptor<ContactData>()
        verify(target).push(eq(userId), captor.capture(), eq(null))
        val data = captor.firstValue
        assert(data.email == "a@b.c")
        assert(data.firstName == "A")
    }
}
