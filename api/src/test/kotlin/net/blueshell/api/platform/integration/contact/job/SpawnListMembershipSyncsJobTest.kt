package net.blueshell.api.platform.integration.contact.job

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.platform.integration.contact.adapter.ContactListAdapter
import net.blueshell.api.platform.integration.contact.application.job.DispatchListMembershipSyncsJob
import net.blueshell.api.platform.integration.contact.persistence.Contact
import net.blueshell.api.platform.integration.contact.persistence.ContactList
import net.blueshell.api.platform.integration.contact.persistence.ContactListMembership
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactListMembershipRepository
import net.blueshell.api.shared.enums.ContactSystem
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.JobDefinition
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

/**
 * Unit tests for [DispatchListMembershipSyncsJob].
 *
 * No Spring context — instantiate directly with mocks.
 */
class DispatchListMembershipSyncsJobTest {

    private val objectMapper = ObjectMapper()
    private val contactListMembershipRepository: ContactListMembershipRepository = mock()
    private val jobs: TrackedJobDispatcher = mock()

    private fun adapterFor(system: ContactSystem): ContactListAdapter = mock<ContactListAdapter>().also {
        whenever(it.system).thenReturn(system)
    }

    private fun membershipFor(userId: Long, listId: Long): ContactListMembership {
        val contact = mock<Contact>().also { whenever(it.userId).thenReturn(userId) }
        val list = mock<ContactList>().also { whenever(it.id).thenReturn(listId) }
        return mock<ContactListMembership>().also {
            whenever(it.contact).thenReturn(contact)
            whenever(it.contactList).thenReturn(list)
        }
    }

    @Test
    fun `enqueues one SyncListMembershipForSystem job per membership per adapter`() {
        val adapter1 = adapterFor(ContactSystem.LISTMONK)
        val adapter2 = adapterFor(ContactSystem.BREVO)
        val job = DispatchListMembershipSyncsJob(objectMapper, contactListMembershipRepository, listOf(adapter1, adapter2), jobs)

        val memberships = listOf(membershipFor(1L, 10L), membershipFor(2L, 10L))
        whenever(contactListMembershipRepository.findAll()).thenReturn(memberships)

        job.handle(objectMapper.writeValueAsString(ContactJobs.DispatchListMembershipSyncsPayload()))

        // 2 memberships × 2 adapters = 4 enqueue calls
        verify(jobs, times(4)).enqueue(any<JobDefinition<Any>>(), any())
    }

    @Test
    fun `does nothing when no memberships exist`() {
        val adapter = adapterFor(ContactSystem.LISTMONK)
        val job = DispatchListMembershipSyncsJob(objectMapper, contactListMembershipRepository, listOf(adapter), jobs)

        whenever(contactListMembershipRepository.findAll()).thenReturn(emptyList())

        job.handle(objectMapper.writeValueAsString(ContactJobs.DispatchListMembershipSyncsPayload()))

        verifyNoInteractions(jobs)
    }

    @Test
    fun `continues when enqueue throws for one membership`() {
        val adapter = adapterFor(ContactSystem.LISTMONK)
        val job = DispatchListMembershipSyncsJob(objectMapper, contactListMembershipRepository, listOf(adapter), jobs)

        val memberships = listOf(membershipFor(1L, 10L), membershipFor(2L, 10L))
        whenever(contactListMembershipRepository.findAll()).thenReturn(memberships)
        whenever(jobs.enqueue(any<JobDefinition<Any>>(), any()))
            .thenThrow(RuntimeException("enqueue failure"))
            .thenReturn(null)

        job.handle(objectMapper.writeValueAsString(ContactJobs.DispatchListMembershipSyncsPayload()))

        // 2 attempts were made despite the first failure
        verify(jobs, times(2)).enqueue(any<JobDefinition<Any>>(), any())
    }
}
