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
class SyncContactJobIT : UserTestSupport() {

    @Autowired
    private lateinit var executor: JobExecutor

    @Autowired
    private lateinit var jobs: TrackedJobDispatcher

    @Autowired
    private lateinit var contactRepository: ContactRepository

    @Autowired
    private lateinit var mockContactAdapter: MockContactAdapter

    @BeforeEach
    fun clearMocks() {
        mockContactAdapter.clear()
    }

    @Test
    fun `sync contact creates contact DB record and dispatches per-system job`() {
        val user = createUserWithRole(Role.MEMBER)
        val execution = jobs.enqueue(
            ContactJobs.SyncContact,
            ContactJobs.SyncContactPayload(user.id!!)
        )!!

        // Run SyncContact synchronously
        executor.execute(jobExecutions.findById(execution.id!!).orElseThrow())

        // Contact DB record created synchronously
        assertThat(contactRepository.findByUserId(user.id!!))
            .describedAs("Contact should be created in DB after sync")
            .isNotNull()

        // SyncContactToSystem job dispatched (not another SyncContact job)
        assertThat(findJobsByType(ContactJobs.SyncContact.type))
            .describedAs("SyncContact should not re-enqueue itself")
            .hasSize(1)
        assertThat(findJobsByType(ContactJobs.SyncContactToSystem.type))
            .describedAs("SyncContactToSystem should be dispatched per active adapter")
            .isNotEmpty()

        val updatedExecution = jobExecutions.findById(execution.id!!).orElseThrow()
        assertThat(updatedExecution.status).isEqualTo(JobExecutionStatus.SUCCESS)
    }

    @Test
    fun `sync contact sends correct contact data to adapter after running SyncContactToSystem`() {
        val user = createUserWithRole(Role.MEMBER)
        val syncExecution = jobs.enqueue(
            ContactJobs.SyncContact,
            ContactJobs.SyncContactPayload(user.id!!)
        )!!

        // Run SyncContact → dispatches SyncContactToSystem
        executor.execute(jobExecutions.findById(syncExecution.id!!).orElseThrow())

        // Run the dispatched SyncContactToSystem job
        val systemExecution = jobExecutions.findAll()
            .first { it.jobType == ContactJobs.SyncContactToSystem.type }
        executor.execute(systemExecution)

        val contacts = mockContactAdapter.getAllContacts()
        assertThat(contacts).hasSize(1)

        val contact = contacts.values.single()
        assertThat(contact.email).isEqualTo(user.email)
        assertThat(contact.firstName).isEqualTo(user.firstName)
        assertThat(contact.lastName).isEqualTo(user.lastName)
        assertThat(contact.phoneNumber).isEqualTo(user.phoneNumber)
        assertThat(contact.newsletter).isEqualTo(user.newsletter)
        assertThat(contact.isMember).isTrue()
    }

    @Test
    fun `sync contact for non-member sets isMember false`() {
        val user = createUserWithRole(Role.GUEST)
        val syncExecution = jobs.enqueue(
            ContactJobs.SyncContact,
            ContactJobs.SyncContactPayload(user.id!!)
        )!!

        executor.execute(jobExecutions.findById(syncExecution.id!!).orElseThrow())

        val systemExecution = jobExecutions.findAll()
            .first { it.jobType == ContactJobs.SyncContactToSystem.type }
        executor.execute(systemExecution)

        val contact = mockContactAdapter.getAllContacts().values.single()
        assertThat(contact.isMember).isFalse()
    }
}
