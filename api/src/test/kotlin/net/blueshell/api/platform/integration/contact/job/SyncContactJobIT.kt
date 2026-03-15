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
    fun `sync contact creates contact DB record and external system entry`() {
        val user = createUserWithRole(Role.MEMBER)
        val execution = jobs.enqueue(
            ContactJobs.SyncContactForSystem,
            SyncContactCommand(user.id!!, ContactSystem.LISTMONK)
        )!!

        executor.execute(jobExecutions.findById(execution.id!!).orElseThrow())

        assertThat(contactRepository.findByUserId(user.id!!))
            .describedAs("Contact should be created in DB after sync")
            .isNotNull()

        assertThat(mockContactAdapter.getAllContacts())
            .describedAs("Contact should exist in external system")
            .hasSize(1)

        val updatedExecution = jobExecutions.findById(execution.id!!).orElseThrow()
        assertThat(updatedExecution.status).isEqualTo(JobExecutionStatus.SUCCESS)
    }

    @Test
    fun `sync contact sends correct contact data to external system`() {
        val user = createUserWithRole(Role.MEMBER)
        val execution = jobs.enqueue(
            ContactJobs.SyncContactForSystem,
            SyncContactCommand(user.id!!, ContactSystem.LISTMONK)
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
            ContactJobs.SyncContactForSystem,
            SyncContactCommand(user.id!!, ContactSystem.LISTMONK)
        )!!

        executor.execute(jobExecutions.findById(execution.id!!).orElseThrow())

        val contact = mockContactAdapter.getAllContacts().values.single()
        assertThat(contact.isMember).isFalse()
    }

    @Test
    fun `sync contact stores external ID in DB`() {
        val user = createUserWithRole(Role.MEMBER)
        val execution = jobs.enqueue(
            ContactJobs.SyncContactForSystem,
            SyncContactCommand(user.id!!, ContactSystem.LISTMONK)
        )!!

        executor.execute(jobExecutions.findById(execution.id!!).orElseThrow())

        val record = contactRepository.findByUserId(user.id!!)!!
        assertThat(record.externalId(ContactSystem.LISTMONK))
            .describedAs("Listmonk external ID should be persisted")
            .isNotNull()
    }
}
