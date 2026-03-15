package net.blueshell.api.domain.contribution.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Security tests for ContributionReminderController.
 *
 * Verifies authorization rules are correctly enforced per ADR-014:
 * - BOARD users can send reminders and list sent reminders
 * - Non-BOARD users cannot access reminder endpoints
 */
@SpringBootTest
class ContributionReminderControllerSecurityTest : UserTestSupport() {
    private fun reminderPayload(userId: Long, contributionPeriodId: Long): String =
        """{"contributionPeriodId":$contributionPeriodId,"userId":$userId}"""

    private fun reminderBatchPayload(userId1: Long, userId2: Long, contributionPeriodId: Long): String =
        """[{"contributionPeriodId":$contributionPeriodId,"userId":$userId1},{"contributionPeriodId":$contributionPeriodId,"userId":$userId2}]"""

    @Nested
    inner class SendContributionReminder {

        @Test
        fun `allows BOARD to send reminders`() {
            val board = createUserWithRole(Role.BOARD)
            val period = createContributionPeriodFixture()
            val user = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/contributionReminders")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(reminderPayload(user.id!!, period.id!!))
            )
                .andExpect(status().isCreated)
        }

        @Test
        fun `denies non-BOARD users from sending reminders`() {
            val member = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            val user = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/contributionReminders")
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(reminderPayload(user.id!!, period.id!!))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val period = createContributionPeriodFixture()
            val user = createUserWithRole(Role.MEMBER)
            mvc.perform(
                post("/contributionReminders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(reminderPayload(user.id!!, period.id!!))
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class SendContributionReminderBatch {

        @Test
        fun `allows BOARD to send batch reminders`() {
            val board = createUserWithRole(Role.BOARD)
            val period = createContributionPeriodFixture()
            val user1 = createUserWithRole(Role.MEMBER)
            val user2 = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/contributionReminders/batch")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(reminderBatchPayload(user1.id!!, user2.id!!, period.id!!))
            )
                .andExpect(status().isCreated)
        }

        @Test
        fun `denies non-BOARD users from sending batch reminders`() {
            val member = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            val user = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/contributionReminders/batch")
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""[${reminderPayload(user.id!!, period.id!!)}]""")
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val period = createContributionPeriodFixture()
            val user = createUserWithRole(Role.MEMBER)
            mvc.perform(
                post("/contributionReminders/batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""[${reminderPayload(user.id!!, period.id!!)}]""")
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class FindContributionReminders {

        @Test
        fun `allows BOARD to list reminders`() {
            val board = createUserWithRole(Role.BOARD)
            val periodId = createContributionPeriodFixture().id!!

            mvc.perform(
                get("/contributionReminders")
                    .param("contributionPeriodId", periodId.toString())
                    .with(bearer(board))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `denies non-BOARD users from listing reminders`() {
            val member = createUserWithRole(Role.MEMBER)
            val periodId = createContributionPeriodFixture().id!!

            mvc.perform(
                get("/contributionReminders")
                    .param("contributionPeriodId", periodId.toString())
                    .with(bearer(member))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val periodId = createContributionPeriodFixture().id!!
            mvc.perform(
                get("/contributionReminders")
                    .param("contributionPeriodId", periodId.toString())
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class RoleHierarchy {

        @Test
        fun `ADMIN can perform BOARD operations`() {
            val admin = createUserWithRole(Role.ADMIN)
            val periodId = createContributionPeriodFixture().id!!

            mvc.perform(
                get("/contributionReminders")
                    .param("contributionPeriodId", periodId.toString())
                    .with(bearer(admin))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `COMMITTEE cannot access reminder endpoints`() {
            val committee = createUserWithRole(Role.COMMITTEE)
            val periodId = createContributionPeriodFixture().id!!

            mvc.perform(
                get("/contributionReminders")
                    .param("contributionPeriodId", periodId.toString())
                    .with(bearer(committee))
            )
                .andExpect(status().isForbidden)
        }
    }
}
