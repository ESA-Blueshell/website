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
    fun `delete contact job removes contact from adapter`() {
        val user = createUserWithRole(Role.MEMBER)

        // First sync the contact to create Contact + adapter entry
        val syncExecution = jobs.enqueue(ContactJobs.SyncContact, ContactJobs.SyncContactPayload(user.id!!))!!
        executor.execute(jobExecutions.findById(syncExecution.id!!).orElseThrow())
        assertThat(mockContactAdapter.getAllContacts()).hasSize(1)

        val execution = jobs.enqueue(
            ContactJobs.DeleteContact,
            ContactJobs.DeleteContactPayload(userId = user.id!!)
        )!!

        executor.execute(jobExecutions.findById(execution.id!!).orElseThrow())

        assertThat(mockContactAdapter.getAllContacts()).isEmpty()

        val updated = jobExecutions.findById(execution.id!!).orElseThrow()
        assertThat(updated.status).isEqualTo(JobExecutionStatus.SUCCESS)
    }

    @Test
    fun `delete contact job also removes contact from all lists`() {
        val user = createUserWithRole(Role.MEMBER)

        // First sync to create Contact
        val syncExecution = jobs.enqueue(ContactJobs.SyncContact, ContactJobs.SyncContactPayload(user.id!!))!!
        executor.execute(jobExecutions.findById(syncExecution.id!!).orElseThrow())

        val contactId = mockContactAdapter.getAllContacts().keys.single()
        val listId = mockContactAdapter.createList("Test List", "testFolder")
        mockContactAdapter.addToList(contactId, listId)
        assertThat(mockContactAdapter.isInList(contactId, listId)).isTrue()

        val execution = jobs.enqueue(
            ContactJobs.DeleteContact,
            ContactJobs.DeleteContactPayload(userId = user.id!!)
        )!!

        executor.execute(jobExecutions.findById(execution.id!!).orElseThrow())

        assertThat(mockContactAdapter.getAllContacts()).isEmpty()
        assertThat(mockContactAdapter.isInList(contactId, listId)).isFalse()

        val updated = jobExecutions.findById(execution.id!!).orElseThrow()
        assertThat(updated.status).isEqualTo(JobExecutionStatus.SUCCESS)
    }
}
