package net.blueshell.api.domain.user.web

import net.blueshell.api.platform.integration.mock.MockContactAdapter
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
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * End-to-end test verifying that deleting a user with an external contact
 * removes the contact from the external system via the DeleteContact job.
 */
@SpringBootTest
@TestPropertySource(properties = ["app.jobs.auto-dispatch=true"])
class UserDeleteContactSyncIT : UserTestSupport() {

    @Autowired
    private lateinit var mockContactAdapter: MockContactAdapter

    @Autowired
    private lateinit var jobs: TrackedJobDispatcher

    @BeforeEach
    fun clearMocks() {
        mockContactAdapter.clear()
    }

    @Test
    fun `deleting user with contact removes contact from external system`() {
        val board = createUserWithRole(Role.BOARD)
        val member = createUserWithRole(Role.MEMBER)

        // Sync contact to assign contactId via auto-dispatch
        enqueueInTransaction {
            jobs.enqueue(
                ContactJobs.SyncContact,
                ContactJobs.SyncContactPayload(member.id!!)
            )
        }
        awaitJobStatus(ContactJobs.SyncContact.type, JobExecutionStatus.SUCCESS)

        assertThat(mockContactAdapter.getAllContacts())
            .describedAs("Contact should exist after sync")
            .isNotEmpty()

        // Delete user via HTTP
        mvc.perform(
            delete("/users/{userId}", member.id)
                .with(bearer(board))
        )
            .andExpect(status().isNoContent)

        // Await DeleteContact job via auto-dispatch
        awaitJobStatus(ContactJobs.DeleteContact.type, JobExecutionStatus.SUCCESS)

        assertThat(mockContactAdapter.getAllContacts())
            .describedAs("Contact should be removed after user deletion")
            .isEmpty()
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun enqueueInTransaction(enqueue: () -> Unit) {
        transactionTemplate.executeWithoutResult {
            enqueue()
        }
    }

    private fun awaitJobStatus(
        jobType: String,
        expected: JobExecutionStatus,
        timeoutMs: Long = 5_000,
        pollMs: Long = 100
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val executions = findJobsByType(jobType)
            if (executions.any { it.status == expected }) return
            Thread.sleep(pollMs)
        }

        val executions = findJobsByType(jobType)
        assertThat(executions)
            .describedAs("Expected at least one job execution for type $jobType")
            .isNotEmpty

        val statuses = executions.map { it.status }
        assertThat(statuses)
            .describedAs("Expected at least one $jobType job with status $expected")
            .contains(expected)
    }
}
