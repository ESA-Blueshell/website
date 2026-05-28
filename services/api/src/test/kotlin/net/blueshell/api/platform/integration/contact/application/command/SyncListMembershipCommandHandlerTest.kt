package net.blueshell.api.platform.integration.contact.application.command

import net.blueshell.api.platform.integration.contact.adapter.ContactListAdapter
import net.blueshell.api.platform.integration.contact.persistence.Contact
import net.blueshell.api.platform.integration.contact.persistence.ContactList
import net.blueshell.api.platform.integration.contact.persistence.ContactListMembership
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactListMembershipRepository
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactListRepository
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactRepository
import net.blueshell.api.shared.enums.ContactSystem
import net.blueshell.api.shared.job.NonRetryableJobException
import net.blueshell.api.shared.job.SyncListMembershipCommand
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.mockito.kotlin.eq
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional

/**
 * Unit tests for [SyncListMembershipCommandHandler].
 *
 * No Spring context — instantiate directly with mocks.
 */
class SyncListMembershipCommandHandlerTest {

    private val listAdapter: ContactListAdapter = mock()
    private val contactRepository: ContactRepository = mock()
    private val contactListRepository: ContactListRepository = mock()
    private val contactListMembershipRepository: ContactListMembershipRepository = mock()
    private val jobs: TrackedJobDispatcher = mock()

    private val handler = SyncListMembershipCommandHandler(
        listAdapters = listOf(listAdapter),
        contactRepository = contactRepository,
        contactListRepository = contactListRepository,
        contactListMembershipRepository = contactListMembershipRepository,
        jobs = jobs,
    )

    private val userId = 10L
    private val contactListId = 20L
    private val system = ContactSystem.BREVO
    private val externalContactId = 100L
    private val externalListId = 200L
    private val command = SyncListMembershipCommand(userId, contactListId, system)

    @BeforeEach
    fun setUp() {
        whenever(listAdapter.system).thenReturn(ContactSystem.BREVO)
    }

    @Test
    fun `adds contact to external list when active membership exists`() {
        val contact = contactWithExternalId()
        val list = listWithExternalId()
        val membership = mock<ContactListMembership>()

        whenever(contactRepository.findByUserId(userId)).thenReturn(contact)
        whenever(contactListRepository.findById(contactListId)).thenReturn(Optional.of(list))
        whenever(contactListMembershipRepository.findByContactIdAndContactListId(contact.id!!, contactListId))
            .thenReturn(membership)

        handler.handle(command)

        verify(listAdapter).addToList(externalContactId, externalListId)
        verify(listAdapter, never()).removeFromList(any(), any())
    }

    @Test
    fun `removes contact from external list when no membership exists`() {
        val contact = contactWithExternalId()
        val list = listWithExternalId()

        whenever(contactRepository.findByUserId(userId)).thenReturn(contact)
        whenever(contactListRepository.findById(contactListId)).thenReturn(Optional.of(list))
        whenever(contactListMembershipRepository.findByContactIdAndContactListId(contact.id!!, contactListId))
            .thenReturn(null)

        handler.handle(command)

        verify(listAdapter).removeFromList(externalContactId, externalListId)
        verify(listAdapter, never()).addToList(any(), any())
    }

    @Test
    fun `throws retryable exception when membership exists but contact not yet synced`() {
        val contact = mock<Contact>().also { whenever(it.id).thenReturn(1L) }
        whenever(contact.externalId(system)).thenReturn(null)  // not yet synced
        val list = listWithExternalId()
        val membership = mock<ContactListMembership>()

        whenever(contactRepository.findByUserId(userId)).thenReturn(contact)
        whenever(contactListRepository.findById(contactListId)).thenReturn(Optional.of(list))
        whenever(contactListMembershipRepository.findByContactIdAndContactListId(contact.id!!, contactListId))
            .thenReturn(membership)

        // Should throw a retryable exception (not NonRetryableJobException)
        val thrown = assertThrows(RuntimeException::class.java) {
            handler.handle(command)
        }
        assert(thrown !is NonRetryableJobException) {
            "Expected retryable exception, got NonRetryableJobException: ${thrown.message}"
        }
    }

    @Test
    fun `throws NonRetryableJobException when membership exists but list has no external ID`() {
        val contact = contactWithExternalId()
        val list = mock<ContactList>().also {
            whenever(it.id).thenReturn(contactListId)
            whenever(it.externalListId(system)).thenReturn(null)
        }
        val membership = mock<ContactListMembership>()

        whenever(contactRepository.findByUserId(userId)).thenReturn(contact)
        whenever(contactListRepository.findById(contactListId)).thenReturn(Optional.of(list))
        whenever(contactListMembershipRepository.findByContactIdAndContactListId(contact.id!!, contactListId))
            .thenReturn(membership)

        assertThrows(NonRetryableJobException::class.java) {
            handler.handle(command)
        }
    }

    @Test
    fun `skips removal when no external IDs available`() {
        val contact = mock<Contact>().also {
            whenever(it.id).thenReturn(1L)
            whenever(it.externalId(system)).thenReturn(null)
        }

        whenever(contactRepository.findByUserId(userId)).thenReturn(contact)
        whenever(contactListRepository.findById(contactListId)).thenReturn(Optional.empty())
        whenever(contactListMembershipRepository.findByContactIdAndContactListId(any(), any())).thenReturn(null)

        handler.handle(command)

        verify(listAdapter, never()).removeFromList(any(), any())
        verify(listAdapter, never()).addToList(any(), any())
    }

    @Test
    fun `throws NonRetryableJobException when no adapter registered for system`() {
        val handlerNoAdapters = SyncListMembershipCommandHandler(
            listAdapters = emptyList(),
            contactRepository = contactRepository,
            contactListRepository = contactListRepository,
            contactListMembershipRepository = contactListMembershipRepository,
            jobs = jobs,
        )

        assertThrows(NonRetryableJobException::class.java) {
            handlerNoAdapters.handle(command)
        }
    }

    @Test
    fun `missing contact pairing enqueues a sync and throws retryable`() {
        val contact = Contact(userId = userId).also { it.id = 1L }  // no external id yet
        val membership = mock<ContactListMembership>()
        val list = listWithExternalId()
        whenever(contactRepository.findByUserId(userId)).thenReturn(contact)
        whenever(contactListRepository.findById(contactListId)).thenReturn(Optional.of(list))
        whenever(contactListMembershipRepository.findByContactIdAndContactListId(1L, contactListId)).thenReturn(membership)

        assertThrows(RuntimeException::class.java) { handler.handle(command) }

        verify(jobs).enqueue(
            eq(net.blueshell.api.shared.job.ContactJobs.SyncContact),
            eq(net.blueshell.api.shared.job.ContactJobs.SyncContactPayload(userId)),
        )
        verify(listAdapter, never()).addToList(any(), any())
    }

    @Test
    fun `contact gone upstream clears the pairing, enqueues a sync, and throws retryable`() {
        val contact = contactWithExternalId()
        val membership = mock<ContactListMembership>()
        val list = listWithExternalId()
        whenever(contactRepository.findByUserId(userId)).thenReturn(contact)
        whenever(contactListRepository.findById(contactListId)).thenReturn(Optional.of(list))
        whenever(contactListMembershipRepository.findByContactIdAndContactListId(1L, contactListId)).thenReturn(membership)
        whenever(listAdapter.addToList(externalContactId, externalListId))
            .thenThrow(
                net.blueshell.api.platform.integration.contact.adapter.ExternalContactGoneException(
                    system, externalContactId,
                )
            )

        assertThrows(RuntimeException::class.java) { handler.handle(command) }

        // Local pairing for BREVO is cleared so the next attempt won't reuse
        // the dead id, and a contact sync is queued to repair it.
        assertThat(contact.externalId(system)).isNull()
        verify(contactRepository).save(contact)
        verify(jobs).enqueue(
            eq(net.blueshell.api.shared.job.ContactJobs.SyncContact),
            eq(net.blueshell.api.shared.job.ContactJobs.SyncContactPayload(userId)),
        )
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun contactWithExternalId(): Contact {
        val contact = Contact(userId = userId).also { it.id = 1L }
        contact.setExternalId(system, externalContactId)
        return contact
    }

    private fun listWithExternalId(): ContactList {
        val list = mock<ContactList>()
        whenever(list.id).thenReturn(contactListId)
        whenever(list.externalListId(system)).thenReturn(externalListId)
        return list
    }
}
