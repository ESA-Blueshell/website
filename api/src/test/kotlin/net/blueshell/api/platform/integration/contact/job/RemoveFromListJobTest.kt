package net.blueshell.api.platform.integration.contact.job

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.domain.user.application.contact.ContactSystem
import net.blueshell.api.domain.user.application.contact.ListSyncAdapter
import net.blueshell.api.platform.integration.contact.application.job.RemoveFromListJob
import net.blueshell.api.platform.integration.contact.persistence.Contact
import net.blueshell.api.platform.integration.contact.persistence.ContactList
import net.blueshell.api.platform.integration.contact.persistence.ListmonkContact
import net.blueshell.api.platform.integration.contact.persistence.ListmonkList
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactListRepository
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactRepository
import net.blueshell.api.shared.job.ContactJobs
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional

/**
 * Unit tests for [RemoveFromListJob].
 *
 * No Spring context — instantiate directly with mocks.
 */
class RemoveFromListJobTest {

    private val objectMapper = ObjectMapper()
    private val listmonkAdapter: ListSyncAdapter = mock {
        whenever(mock.system).thenReturn(ContactSystem.LISTMONK)
    }
    private val contactRepository: ContactRepository = mock()
    private val contactListRepository: ContactListRepository = mock()

    private val job = RemoveFromListJob(
        objectMapper = objectMapper,
        adapters = listOf(listmonkAdapter),
        contactRepository = contactRepository,
        contactListRepository = contactListRepository,
    )

    private val userId = 1L
    private val listId = 10L

    @Test
    fun `calls removeFromList with correct external IDs on success`() {
        val contact = contactWithExternalId(userId, 42L)
        val list = listWithExternalId(listId, 100L)

        whenever(contactRepository.findByUserId(userId)).thenReturn(contact)
        whenever(contactListRepository.findById(listId)).thenReturn(Optional.of(list))

        job.handle(payload(ContactJobs.RemoveFromListPayload(userId, listId, ContactSystem.LISTMONK)))

        verify(listmonkAdapter).removeFromList(42L, 100L)
    }

    @Test
    fun `is no-op when contact has no externalId for the system`() {
        val contact = Contact(userId = userId).apply { id = 1L }  // no listmonkContact
        whenever(contactRepository.findByUserId(userId)).thenReturn(contact)

        job.handle(payload(ContactJobs.RemoveFromListPayload(userId, listId, ContactSystem.LISTMONK)))

        verify(listmonkAdapter, never()).removeFromList(any(), any())
    }

    @Test
    fun `is no-op when contact record is missing`() {
        whenever(contactRepository.findByUserId(userId)).thenReturn(null)

        job.handle(payload(ContactJobs.RemoveFromListPayload(userId, listId, ContactSystem.LISTMONK)))

        verify(listmonkAdapter, never()).removeFromList(any(), any())
    }

    @Test
    fun `is no-op when list has no externalId for the system`() {
        val contact = contactWithExternalId(userId, 42L)
        val list = ContactList(name = "List").apply { id = listId }  // no listmonkList

        whenever(contactRepository.findByUserId(userId)).thenReturn(contact)
        whenever(contactListRepository.findById(listId)).thenReturn(Optional.of(list))

        job.handle(payload(ContactJobs.RemoveFromListPayload(userId, listId, ContactSystem.LISTMONK)))

        verify(listmonkAdapter, never()).removeFromList(any(), any())
    }

    @Test
    fun `skips gracefully when no adapter registered for system`() {
        job.handle(payload(ContactJobs.RemoveFromListPayload(userId, listId, ContactSystem.BREVO)))

        verify(listmonkAdapter, never()).removeFromList(any(), any())
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun payload(p: Any) = objectMapper.writeValueAsString(p)

    private fun contactWithExternalId(userId: Long, externalId: Long): Contact {
        val c = Contact(userId = userId).apply { id = userId }
        c.listmonkContact = ListmonkContact(contact = c, externalId = externalId)
        return c
    }

    private fun listWithExternalId(listId: Long, externalListId: Long): ContactList {
        val l = ContactList(name = "List").apply { id = listId }
        l.listmonkList = ListmonkList(list = l, externalId = externalListId)
        return l
    }
}
