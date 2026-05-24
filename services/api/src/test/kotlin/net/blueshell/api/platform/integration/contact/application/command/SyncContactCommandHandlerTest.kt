package net.blueshell.api.platform.integration.contact.application.command

import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.platform.integration.contact.adapter.ContactAdapter
import net.blueshell.api.platform.integration.contact.adapter.ContactData
import net.blueshell.api.platform.integration.contact.persistence.Contact
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactRepository
import net.blueshell.api.platform.integration.queue.SyncLock
import net.blueshell.api.platform.integration.queue.SyncLockBusyException
import net.blueshell.api.shared.enums.ContactSystem
import net.blueshell.api.shared.job.NonRetryableJobException
import net.blueshell.api.shared.job.SyncContactCommand
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for [SyncContactCommandHandler].
 *
 * No Spring context — instantiate directly with mocks. The injected
 * [SyncLock] is a pass-through that simply invokes the action; lock
 * acquisition itself is exercised separately.
 */
class SyncContactCommandHandlerTest {

    private val adapter: ContactAdapter = mock()
    private val contactRepository: ContactRepository = mock()
    private val userService: UserService = mock()
    private val passthroughLock: SyncLock = object : SyncLock {
        override fun <T> withLock(name: String, timeoutSeconds: Int, action: () -> T): T = action()
    }

    private val handler = SyncContactCommandHandler(
        contactAdapters = listOf(adapter),
        contactRepository = contactRepository,
        userService = userService,
        syncLock = passthroughLock,
    )

    private val userId = 42L
    private val externalId = 1000L
    private val system = ContactSystem.LISTMONK
    private val command = SyncContactCommand(userId, system)

    @BeforeEach
    fun setUp() {
        whenever(adapter.system).thenReturn(ContactSystem.LISTMONK)

        val user = mock<User>()
        whenever(user.email).thenReturn("test@example.com")
        whenever(user.firstName).thenReturn("Test")
        whenever(user.lastName).thenReturn("User")
        whenever(user.phoneNumber).thenReturn(null)
        whenever(user.newsletter).thenReturn(false)
        whenever(user.roles).thenReturn(mutableSetOf())
        whenever(userService.findById(userId)).thenReturn(user)
    }

    @Test
    fun `creates contact in external system and stores external ID when no contact record exists`() {
        whenever(contactRepository.findByUserIdIncludingDeleted(userId)).thenReturn(null)
        val newContact = Contact(userId = userId).also { it.id = 99L }
        whenever(contactRepository.save(any<Contact>())).thenReturn(newContact)
        whenever(adapter.createContact(any())).thenReturn(externalId)

        handler.handle(command)

        verify(adapter).createContact(any())
        // first save: persist new Contact to get a DB ID; second save: store the external ID
        verify(contactRepository, times(2)).save(any())
    }

    @Test
    fun `updates contact in external system when external ID exists and snapshot is stale`() {
        val contact = Contact(userId = userId).also { it.id = 99L }
        contact.setExternalId(system, externalId)
        // Snapshot left at defaults — does not match the user's current data.
        whenever(contactRepository.findByUserIdIncludingDeleted(userId)).thenReturn(contact)
        whenever(contactRepository.save(any<Contact>())).thenReturn(contact)

        handler.handle(command)

        verify(adapter).updateContact(eq(externalId), any<ContactData>())
        verify(adapter, never()).createContact(any())
    }

    @Test
    fun `skips external update when stored snapshot already matches desired state`() {
        val contact = Contact(userId = userId).also { it.id = 99L }
        contact.setExternalId(system, externalId)
        contact.updateSnapshot(
            email = "test@example.com",
            firstName = "Test",
            lastName = "User",
            phoneNumber = null,
            newsletter = false,
            isMember = false,
        )
        whenever(contactRepository.findByUserIdIncludingDeleted(userId)).thenReturn(contact)

        handler.handle(command)

        verify(adapter, never()).updateContact(any(), any())
        verify(adapter, never()).createContact(any())
        verify(contactRepository, never()).save(any())
    }

    @Test
    fun `deletes contact from external system when contact is soft-deleted`() {
        val contact = Contact(userId = userId).also { it.id = 99L }
        contact.setExternalId(system, externalId)
        // Mark as soft-deleted by making isSoftDeleted return true
        val deletedContact = mock<Contact>()
        whenever(deletedContact.isSoftDeleted).thenReturn(true)
        whenever(deletedContact.externalId(system)).thenReturn(externalId)
        whenever(contactRepository.findByUserIdIncludingDeleted(userId)).thenReturn(deletedContact)
        whenever(contactRepository.save(any<Contact>())).thenReturn(deletedContact)

        handler.handle(command)

        verify(adapter).deleteContact(externalId)
        verify(adapter, never()).createContact(any())
        verify(adapter, never()).updateContact(any(), any())
    }

    @Test
    fun `skips deletion when soft-deleted contact has no external ID`() {
        val deletedContact = mock<Contact>()
        whenever(deletedContact.isSoftDeleted).thenReturn(true)
        whenever(deletedContact.externalId(system)).thenReturn(null)
        whenever(contactRepository.findByUserIdIncludingDeleted(userId)).thenReturn(deletedContact)

        handler.handle(command)

        verify(adapter, never()).deleteContact(any())
        verify(contactRepository, never()).save(any())
    }

    @Test
    fun `throws NonRetryableJobException when no adapter registered for system`() {
        val handlerNoAdapters = SyncContactCommandHandler(
            contactAdapters = emptyList(),
            contactRepository = contactRepository,
            userService = userService,
            syncLock = passthroughLock,
        )

        assertThrows(NonRetryableJobException::class.java) {
            handlerNoAdapters.handle(command)
        }
    }

    @Test
    fun `propagates lock-busy as retryable when sync lock cannot be acquired`() {
        val busyLock: SyncLock = object : SyncLock {
            override fun <T> withLock(name: String, timeoutSeconds: Int, action: () -> T): T {
                throw SyncLockBusyException("lock '$name' busy")
            }
        }
        val handlerBusy = SyncContactCommandHandler(
            contactAdapters = listOf(adapter),
            contactRepository = contactRepository,
            userService = userService,
            syncLock = busyLock,
        )

        assertThrows(SyncLockBusyException::class.java) {
            handlerBusy.handle(command)
        }
        verify(adapter, never()).createContact(any())
        verify(adapter, never()).updateContact(any(), any())
        verify(adapter, never()).deleteContact(any())
    }
}
