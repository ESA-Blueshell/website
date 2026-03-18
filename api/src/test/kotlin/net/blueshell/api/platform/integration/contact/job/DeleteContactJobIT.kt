package net.blueshell.api.platform.integration.contact.job

import net.blueshell.api.domain.user.application.contact.ContactData
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

    @BeforeEach
    fun clearMocks() {
        mockContactAdapter.clear()
    }

    @Test
    fun `delete contact job removes contact from adapter`() {
        val user = createUserWithRole(Role.MEMBER)

        val contactId = mockContactAdapter.syncContact(
            user.id!!,
            null,
            ContactData(
                email = user.email,
                firstName = user.firstName,
                lastName = user.lastName,
                phoneNumber = user.phoneNumber,
                newsletter = user.newsletter,
                isMember = true
            )
        )
        assertThat(mockContactAdapter.getAllContacts()).hasSize(1)

        val execution = jobs.enqueue(
            ContactJobs.DeleteContact,
            ContactJobs.DeleteContactPayload(userId = user.id!!, contactId = contactId.toLong())
        )!!

        executor.execute(jobExecutions.findById(execution.id!!).orElseThrow())

        assertThat(mockContactAdapter.getAllContacts()).isEmpty()

        val updated = jobExecutions.findById(execution.id!!).orElseThrow()
        assertThat(updated.status).isEqualTo(JobExecutionStatus.SUCCESS)
    }

    @Test
    fun `delete contact job also removes contact from all lists`() {
        val user = createUserWithRole(Role.MEMBER)

        val contactId = mockContactAdapter.syncContact(
            user.id!!,
            null,
            ContactData(
                email = user.email,
                firstName = user.firstName,
                lastName = user.lastName,
                phoneNumber = user.phoneNumber,
                newsletter = user.newsletter,
                isMember = true
            )
        )

        val listId = mockContactAdapter.createList("Test List", "testFolder")
        mockContactAdapter.addToList(listId, contactId)

        assertThat(mockContactAdapter.getAllLists().values.single().contactIds).hasSize(1)

        val execution = jobs.enqueue(
            ContactJobs.DeleteContact,
            ContactJobs.DeleteContactPayload(userId = user.id!!, contactId = contactId.toLong())
        )!!

        executor.execute(jobExecutions.findById(execution.id!!).orElseThrow())

        assertThat(mockContactAdapter.getAllContacts()).isEmpty()
        assertThat(mockContactAdapter.getAllLists().values.single().contactIds).isEmpty()

        val updated = jobExecutions.findById(execution.id!!).orElseThrow()
        assertThat(updated.status).isEqualTo(JobExecutionStatus.SUCCESS)
    }
}
