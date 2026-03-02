package net.blueshell.api.domain.contribution.web

import net.blueshell.api.platform.integration.mock.MockContactAdapter
import net.blueshell.api.shared.enums.JobExecutionStatus
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Integration tests verifying that marking contributions as paid/unpaid
 * correctly syncs the user's contact list membership in Brevo via the
 * [SyncListMembership][net.blueshell.api.platform.integration.contact.job.SyncListMembershipJob] job.
 *
 * Auto-dispatch is enabled so jobs execute after the transaction commits,
 * matching the production call path.
 */
@SpringBootTest
@TestPropertySource(properties = ["app.jobs.auto-dispatch=true"])
class ContributionContactSyncIT : UserTestSupport() {

    @Autowired
    private lateinit var mockContactAdapter: MockContactAdapter

    @BeforeEach
    fun clearMocks() {
        mockContactAdapter.clear()
    }

    @Test
    fun `marking contribution as paid adds user to contribution period list`() {
        val board = createUserWithRole(Role.BOARD)
        val member = createUserWithRole(Role.MEMBER)
        val period = createContributionPeriodFixture()

        mvc.perform(
            post("/contributions")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":${member.id},"contributionPeriodId":${period.id}}""")
        )
            .andExpect(status().isCreated)

        awaitJobStatus(ContactJobs.SyncListMembership.type, JobExecutionStatus.SUCCESS)

        val lists = mockContactAdapter.getAllLists()
        assertThat(lists).hasSize(1)

        val list = lists.values.single()
        assertThat(list.contactIds).hasSize(1)

        val contacts = mockContactAdapter.getAllContacts()
        val contact = contacts.values.single()
        assertThat(contact.email).isEqualTo(member.email)
        assertThat(list.contactIds).contains(contact.contactId)
    }

    @Test
    fun `marking contribution as unpaid removes user from contribution period list`() {
        val board = createUserWithRole(Role.BOARD)
        val member = createUserWithRole(Role.MEMBER)
        val period = createContributionPeriodFixture()

        // First mark as paid
        mvc.perform(
            post("/contributions")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":${member.id},"contributionPeriodId":${period.id}}""")
        )
            .andExpect(status().isCreated)

        awaitJobStatus(ContactJobs.SyncListMembership.type, JobExecutionStatus.SUCCESS)

        // Verify the user was added to the list
        val listBefore = mockContactAdapter.getAllLists().values.single()
        assertThat(listBefore.contactIds).hasSize(1)

        // Now mark as unpaid
        mvc.perform(
            delete(
                "/contributionPeriods/{contributionPeriodId}/users/{userId}/contributions",
                period.id,
                member.id
            )
                .with(bearer(board))
        )
            .andExpect(status().isNoContent)

        awaitJobSuccess(ContactJobs.SyncListMembership.type, expectedCount = 2)

        // Verify the user was removed from the list
        val listAfter = mockContactAdapter.getAllLists().values.single()
        assertThat(listAfter.contactIds).isEmpty()
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Polls until the first job of the given type reaches the expected status,
     * or fails after timeout.
     */
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

    /**
     * Polls until [expectedCount] jobs of the given type have reached SUCCESS.
     */
    private fun awaitJobSuccess(
        jobType: String,
        expectedCount: Int,
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
