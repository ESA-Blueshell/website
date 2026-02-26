package net.blueshell.api.platform.integration.contact.job

import net.blueshell.api.domain.user.application.UserService
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
    private lateinit var users: UserService

    @Autowired
    private lateinit var mockContactAdapter: MockContactAdapter

    @BeforeEach
    fun clearMocks() {
        mockContactAdapter.clear()
    }

    @Test
    fun `sync contact assigns contact id without scheduling another sync job`() {
        val user = createUserWithRole(Role.MEMBER)
        val execution = jobs.enqueue(
            ContactJobs.SyncContact,
            ContactJobs.SyncContactPayload(user.id!!)
        )!!
        assertThat(findJobsByType(ContactJobs.SyncContact.type)).hasSize(1)
        executor.execute(jobExecutions.findById(execution.id!!).orElseThrow())

        val jobsAfterHandling = findJobsByType(ContactJobs.SyncContact.type)
        assertThat(jobsAfterHandling)
            .describedAs("Contact-id linkage update should not re-enqueue contact.sync jobs")
            .hasSize(1)

        val refreshedUser = users.findById(user.id!!)
        assertThat(refreshedUser.contactId).isNotNull

        val updatedExecution = jobExecutions.findById(execution.id!!).orElseThrow()
        assertThat(updatedExecution.status).isEqualTo(JobExecutionStatus.SUCCESS)
    }

    @Test
    fun `sync contact sends correct contact data to adapter`() {
        val user = createUserWithRole(Role.MEMBER)
        val execution = jobs.enqueue(
            ContactJobs.SyncContact,
            ContactJobs.SyncContactPayload(user.id!!)
        )!!

        executor.execute(jobExecutions.findById(execution.id!!).orElseThrow())

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
        val execution = jobs.enqueue(
            ContactJobs.SyncContact,
            ContactJobs.SyncContactPayload(user.id!!)
        )!!

        executor.execute(jobExecutions.findById(execution.id!!).orElseThrow())

        val contact = mockContactAdapter.getAllContacts().values.single()
        assertThat(contact.isMember).isFalse()
    }
}
