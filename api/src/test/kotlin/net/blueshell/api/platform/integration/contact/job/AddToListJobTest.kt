package net.blueshell.api.platform.integration.contact.job

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.domain.user.application.contact.ContactSystem
import net.blueshell.api.domain.user.application.contact.ContactSystemAdapter
import net.blueshell.api.platform.integration.contact.application.job.AddToListJob
import net.blueshell.api.platform.integration.contact.persistence.Contact
import net.blueshell.api.platform.integration.contact.persistence.ContactList
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactListRepository
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactRepository
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.NonRetryableJobException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional

/**
 * Unit tests for [AddToListJob].
 *
 * No Spring context — instantiate directly with mocks.
 */
class AddToListJobTest {

    private val objectMapper = ObjectMapper()
    private val listmonkAdapter: ContactSystemAdapter = mock {
        whenever(mock.system).thenReturn(ContactSystem.LISTMONK)
    }
    private val contactRepository: ContactRepository = mock()
    private val contactListRepository: ContactListRepository = mock()

    private val job = AddToListJob(
        objectMapper = objectMapper,
        adapters = listOf(listmonkAdapter),
        contactRepository = contactRepository,
        contactListRepository = contactListRepository,
    )

    private val userId = 1L
    private val listId = 10L

    @Test
    fun `calls addToList with correct external IDs on success`() {
        val contact = contactWithExternalId(userId, 42L)
        val list = listWithExternalId(listId, 100L)

        whenever(contactRepository.findByUserId(userId)).thenReturn(contact)
        whenever(contactListRepository.findById(listId)).thenReturn(Optional.of(list))

        job.handle(payload(ContactJobs.AddToListPayload(userId, listId, ContactSystem.LISTMONK)))

        verify(listmonkAdapter).addToList(42L, 100L)
    }

    @Test
    fun `throws retryable exception when contact has no externalId yet`() {
        val contact = Contact(userId = userId).apply { id = 1L }  // no external ID set
        whenever(contactRepository.findByUserId(userId)).thenReturn(contact)

        assertThatThrownBy {
            job.handle(payload(ContactJobs.AddToListPayload(userId, listId, ContactSystem.LISTMONK)))
        }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessageContaining("not yet synced")
    }

    @Test
    fun `throws retryable exception when contact record is missing entirely`() {
        whenever(contactRepository.findByUserId(userId)).thenReturn(null)

        assertThatThrownBy {
            job.handle(payload(ContactJobs.AddToListPayload(userId, listId, ContactSystem.LISTMONK)))
        }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessageContaining("not yet synced")
    }

    @Test
    fun `throws NonRetryableJobException when list has no externalId`() {
        val contact = contactWithExternalId(userId, 42L)
        val list = ContactList(name = "List").apply { id = listId }  // no external ID set

        whenever(contactRepository.findByUserId(userId)).thenReturn(contact)
        whenever(contactListRepository.findById(listId)).thenReturn(Optional.of(list))

        assertThatThrownBy {
            job.handle(payload(ContactJobs.AddToListPayload(userId, listId, ContactSystem.LISTMONK)))
        }
            .isInstanceOf(NonRetryableJobException::class.java)
    }

    @Test
    fun `skips gracefully when no adapter registered for system`() {
        job.handle(payload(ContactJobs.AddToListPayload(userId, listId, ContactSystem.BREVO)))

        verify(listmonkAdapter, never()).addToList(any(), any())
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun payload(p: Any) = objectMapper.writeValueAsString(p)

    private fun contactWithExternalId(userId: Long, externalId: Long): Contact {
        val c = Contact(userId = userId).apply { id = userId }
        c.setExternalId(ContactSystem.LISTMONK, externalId)
        return c
    }

    private fun listWithExternalId(listId: Long, externalListId: Long): ContactList {
        val l = ContactList(name = "List").apply { id = listId }
        l.setExternalListId(ContactSystem.LISTMONK, externalListId)
        return l
    }
}
