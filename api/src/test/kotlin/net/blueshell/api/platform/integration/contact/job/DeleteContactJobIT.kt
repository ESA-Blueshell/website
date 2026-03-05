package net.blueshell.api.platform.integration.contact.job

import net.blueshell.api.platform.integration.contact.persistence.repository.ContactRepository
import net.blueshell.api.platform.integration.mock.MockContactAdapter
import net.blueshell.api.platform.integration.queue.JobExecutor
import net.blueshell.api.shared.enums.JobExecutionStatus
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class DeleteContactJobIT : UserTestSupport() {

    @Autowired
    private lateinit var executor: JobExecutor

    @Autowired
    private lateinit var jobs: TrackedJobDispatcher

    @Autowired
    private lateinit var mockContactAdapter: MockContactAdapter

    @Autowired
    private lateinit var contactRepository: ContactRepository

    @BeforeEach
    fun clearMocks() {
        mockContactAdapter.clear()
    }

    @Test
    fun `delete contact job dispatches per-system delete and removes contact from DB`() {
        val user = createUserWithRole(Role.MEMBER)

        // Sync the contact to create Contact DB record + external system entry
        syncContactAndSystem(user.id!!)
        assertThat(mockContactAdapter.getAllContacts()).hasSize(1)

        val deleteExecution = jobs.enqueue(
            ContactJobs.DeleteContact,
            ContactJobs.DeleteContactPayload(userId = user.id!!)
        )!!

        // Run DeleteContact → dispatches DeleteContactFromSystem + removes DB record
        executor.execute(jobExecutions.findById(deleteExecution.id!!).orElseThrow())

        // Run the dispatched DeleteContactFromSystem job
        val systemExecution = jobExecutions.findAll()
            .first { it.jobType == ContactJobs.DeleteContactFromSystem.type }
        executor.execute(systemExecution)

        assertThat(mockContactAdapter.getAllContacts()).isEmpty()
        assertThat(contactRepository.findByUserId(user.id!!)).isNull()

        val updated = jobExecutions.findById(deleteExecution.id!!).orElseThrow()
        assertThat(updated.status).isEqualTo(JobExecutionStatus.SUCCESS)
    }

    @Test
    fun `delete contact job removes contact from all lists`() {
        val user = createUserWithRole(Role.MEMBER)

        // Sync to create Contact + external system entry
        syncContactAndSystem(user.id!!)

        val contactId = mockContactAdapter.getAllContacts().keys.single()
        val listId = mockContactAdapter.createList("Test List", "testFolder")
        mockContactAdapter.addToList(contactId, listId)
        assertThat(mockContactAdapter.isInList(contactId, listId)).isTrue()

        val deleteExecution = jobs.enqueue(
            ContactJobs.DeleteContact,
            ContactJobs.DeleteContactPayload(userId = user.id!!)
        )!!

        executor.execute(jobExecutions.findById(deleteExecution.id!!).orElseThrow())

        val systemExecution = jobExecutions.findAll()
            .first { it.jobType == ContactJobs.DeleteContactFromSystem.type }
        executor.execute(systemExecution)

        assertThat(mockContactAdapter.getAllContacts()).isEmpty()
        assertThat(mockContactAdapter.isInList(contactId, listId)).isFalse()
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Runs SyncContact then SyncContactToSystem synchronously. */
    private fun syncContactAndSystem(userId: Long) {
        val syncExecution = jobs.enqueue(ContactJobs.SyncContact, ContactJobs.SyncContactPayload(userId))!!
        executor.execute(jobExecutions.findById(syncExecution.id!!).orElseThrow())

        val systemExecution = jobExecutions.findAll()
            .first { it.jobType == ContactJobs.SyncContactToSystem.type }
        executor.execute(systemExecution)
    }
}
