package net.blueshell.api.platform.integration.contact.application.command

import net.blueshell.api.platform.integration.contact.adapter.ListAdapter
import net.blueshell.api.platform.integration.contact.persistence.Contact
import net.blueshell.api.platform.integration.contact.persistence.ContactList
import net.blueshell.api.platform.integration.contact.persistence.ContactListMembership
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactListMembershipRepository
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactListRepository
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactRepository
import net.blueshell.api.shared.enums.ContactSystem
import net.blueshell.api.shared.job.NonRetryableJobException
import net.blueshell.api.shared.job.SyncListMembershipCommand
import org.junit.jupiter.api.Assertions.assertThrows
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

    private val listAdapter: ListAdapter = mock()
    private val contactRepository: ContactRepository = mock()
    private val contactListRepository: ContactListRepository = mock()
    private val contactListMembershipRepository: ContactListMembershipRepository = mock()

    private val handler = SyncListMembershipCommandHandler(
        listAdapters = listOf(listAdapter),
        contactRepository = contactRepository,
        contactListRepository = contactListRepository,
        contactListMembershipRepository = contactListMembershipRepository,
    )

    private val userId = 10L
    private val contactListId = 20L
    private val system = ContactSystem.LISTMONK
    private val externalContactId = 100L
    private val externalListId = 200L
    private val command = SyncListMembershipCommand(userId, contactListId, system)

    @BeforeEach
    fun setUp() {
        whenever(listAdapter.system).thenReturn(ContactSystem.LISTMONK)
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
        )

        assertThrows(NonRetryableJobException::class.java) {
            handlerNoAdapters.handle(command)
        }
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
