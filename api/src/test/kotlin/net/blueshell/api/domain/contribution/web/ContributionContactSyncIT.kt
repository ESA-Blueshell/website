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
 * correctly syncs the user's contact list membership via the async job chain:
 * SyncListMembership → SyncContactForSystem + SyncListMembershipForSystem
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

        awaitJobSuccess(ContactJobs.SyncListMembership.type)
        awaitJobSuccess(ContactJobs.SyncContactForSystem.type)
        awaitJobSuccess(ContactJobs.SyncListMembershipForSystem.type)

        val lists = mockContactAdapter.getAllLists()
        assertThat(lists).hasSize(1)
        val listId = lists.keys.single()

        val contacts = mockContactAdapter.getAllContacts()
        assertThat(contacts).hasSize(1)
        val contactId = contacts.keys.single()
        val contact = contacts.values.single()
        assertThat(contact.email).isEqualTo(member.email)
        assertThat(mockContactAdapter.isInList(contactId, listId)).isTrue()
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

        awaitJobSuccess(ContactJobs.SyncListMembership.type)
        awaitJobSuccess(ContactJobs.SyncContactForSystem.type)
        awaitJobSuccess(ContactJobs.SyncListMembershipForSystem.type)

        val listId = mockContactAdapter.getAllLists().keys.single()
        val contactId = mockContactAdapter.getAllContacts().keys.single()
        assertThat(mockContactAdapter.isInList(contactId, listId)).isTrue()

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
        awaitJobSuccess(ContactJobs.SyncListMembershipForSystem.type, expectedCount = 2)

        assertThat(mockContactAdapter.isInList(contactId, listId)).isFalse()
    }

    // ── Helpers ──────────────────────────────────────────────────────────

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
