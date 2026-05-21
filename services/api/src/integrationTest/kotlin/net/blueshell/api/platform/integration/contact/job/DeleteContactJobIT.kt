package net.blueshell.api.platform.integration.contact.job

import net.blueshell.api.platform.integration.contact.persistence.repository.ContactRepository
import net.blueshell.api.platform.integration.mock.MockContactAdapter
import net.blueshell.api.platform.integration.queue.JobExecutor
import net.blueshell.api.shared.enums.ContactSystem
import net.blueshell.api.shared.enums.JobExecutionStatus
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.SyncContactCommand
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
    fun `delete contact job soft-deletes Contact record and removes from external system`() {
        val user = createUserWithRole(Role.MEMBER)

        // Create contact in DB and external system via sync job
        syncContact(user.id!!)
        assertThat(mockContactAdapter.getAllContacts()).hasSize(1)
        assertThat(contactRepository.findByUserId(user.id!!)).isNotNull()

        // Run DeleteContact job
        val deleteExecution = jobs.enqueue(
            ContactJobs.DeleteContact,
            ContactJobs.DeleteContactPayload(userId = user.id!!)
        )!!
        executor.execute(jobExecutions.findById(deleteExecution.id!!).orElseThrow())

        // Contact DB record is soft-deleted (invisible via normal findByUserId)
        assertThat(contactRepository.findByUserId(user.id!!))
            .describedAs("Contact should be soft-deleted and invisible via normal query")
            .isNull()

        // Run the dispatched per-integration sync job (delete path)
        val syncExecution = jobExecutions.findAll()
            .first { it.jobType == ContactJobs.SyncContactToSystem.type && it.id != null && it.id != deleteExecution.id }
        executor.execute(syncExecution)

        assertThat(mockContactAdapter.getAllContacts())
            .describedAs("Contact should be removed from external system")
            .isEmpty()

        val updated = jobExecutions.findById(deleteExecution.id!!).orElseThrow()
        assertThat(updated.status).isEqualTo(JobExecutionStatus.SUCCESS)
    }

    @Test
    fun `delete contact job removes contact from external lists`() {
        val user = createUserWithRole(Role.MEMBER)

        syncContact(user.id!!)

        val contactId = mockContactAdapter.getAllContacts().keys.single()
        val listId = mockContactAdapter.createList("Test List", null)
        mockContactAdapter.addToList(contactId, listId)
        assertThat(mockContactAdapter.isInList(contactId, listId)).isTrue()

        val deleteExecution = jobs.enqueue(
            ContactJobs.DeleteContact,
            ContactJobs.DeleteContactPayload(userId = user.id!!)
        )!!
        executor.execute(jobExecutions.findById(deleteExecution.id!!).orElseThrow())

        val syncExecution = jobExecutions.findAll()
            .first { it.jobType == ContactJobs.SyncContactToSystem.type && it.id != deleteExecution.id }
        executor.execute(syncExecution)

        assertThat(mockContactAdapter.getAllContacts()).isEmpty()
        // Contacts are removed from lists when deleteContact is called on MockContactAdapter
        assertThat(mockContactAdapter.isInList(contactId, listId)).isFalse()
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Runs SyncContactForSystem (via mock) synchronously. */
    private fun syncContact(userId: Long) {
        val syncExecution = jobs.enqueue(
            ContactJobs.SyncContactToSystem,
            SyncContactCommand(userId, ContactSystem.BREVO)
        )!!
        executor.execute(jobExecutions.findById(syncExecution.id!!).orElseThrow())
    }
}
