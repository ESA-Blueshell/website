package net.blueshell.api.domain.user.web

import net.blueshell.api.platform.integration.mock.MockContactAdapter
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
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * End-to-end test verifying that deleting a user with an external contact
 * removes the contact from the external system via the
 * DeleteContact → per-integration sync job chain.
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

        // Sync contact first to assign external ID
        enqueueInTransaction {
            jobs.enqueue(ContactJobs.SyncContactForSystem, SyncContactCommand(member.id!!, ContactSystem.LISTMONK))
        }
        awaitJobSuccess(ContactJobs.SyncContactForSystem.type)

        assertThat(mockContactAdapter.getAllContacts())
            .describedAs("Contact should exist after sync")
            .isNotEmpty()

        // Delete user via HTTP (fires UserDeleted → ErasureListener → DeleteContact job)
        mvc.perform(
            delete("/users/{userId}", member.id)
                .with(bearer(board))
        )
            .andExpect(status().isNoContent)

        // Await DeleteContact + per-integration sync (delete path)
        awaitJobSuccess(ContactJobs.DeleteContact.type)
        awaitJobSuccess(ContactJobs.SyncContactForSystem.type, expectedCount = 2)

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

    private fun awaitJobSuccess(
        jobType: String,
        expectedCount: Int = 1,
        timeoutMs: Long = 5_000,
        pollMs: Long = 100
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val successCount = findJobsByType(jobType).count { it.status == JobExecutionStatus.SUCCESS }
            if (successCount >= expectedCount) return
            Thread.sleep(pollMs)
        }

        val executions = findJobsByType(jobType)
        val successCount = executions.count { it.status == JobExecutionStatus.SUCCESS }
        assertThat(successCount)
            .describedAs(
                "Expected $expectedCount successful $jobType jobs, but found $successCount. " +
                    "Statuses: ${executions.map { "${it.id}=${it.status}" }}"
            )
            .isGreaterThanOrEqualTo(expectedCount)
    }
}
