package net.blueshell.api.domain.contribution.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

/**
 * Server-side hardening: every frontend guard on the bulk-action endpoints is mirrored
 * with jakarta validation on the request DTOs or a handler guard, so a direct API call
 * cannot bypass the rules. See docs/proposals/bulk-actions/REDESIGN.md §2 & §3.
 */
@SpringBootTest
class ContributionBulkControllerValidationTest : UserTestSupport() {

    private fun futureDate(): String = LocalDate.now().plusDays(20).toString()
    private fun pastDate(): String = LocalDate.now().minusDays(1).toString()

    @Nested
    inner class ReminderExecuteValidation {

        @Test
        fun `rejects a past payment due date with 400`() {
            val board = createUserWithRole(Role.BOARD)
            val period = createContributionPeriodFixture()
            val user = createUserWithRole(Role.MEMBER)
            val cutoff = LocalDate.now().toString()

            mvc.perform(
                post("/contributionReminders/bulk/execute")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"userIds":[${user.id}],"contributionPeriodId":${period.id},"cutoffDate":"$cutoff","paymentDueDate":"${pastDate()}"}"""
                    )
            ).andExpect(status().isBadRequest)
        }

        @Test
        fun `rejects a cutoff date outside the contribution period with 400`() {
            val board = createUserWithRole(Role.BOARD)
            val period = createContributionPeriodFixture()
            val user = createUserWithRole(Role.MEMBER)
            // Period ends at now+1mo; this cutoff is far beyond it.
            val cutoff = LocalDate.now().plusMonths(6).toString()

            mvc.perform(
                post("/contributionReminders/bulk/execute")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"userIds":[${user.id}],"contributionPeriodId":${period.id},"cutoffDate":"$cutoff","paymentDueDate":"${futureDate()}"}"""
                    )
            ).andExpect(status().isBadRequest)
        }

        @Test
        fun `rejects more than 1000 user ids with 400`() {
            val board = createUserWithRole(Role.BOARD)
            val period = createContributionPeriodFixture()
            val cutoff = LocalDate.now().toString()
            val ids = (1..1001).joinToString(",")

            mvc.perform(
                post("/contributionReminders/bulk/execute")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"userIds":[$ids],"contributionPeriodId":${period.id},"cutoffDate":"$cutoff","paymentDueDate":"${futureDate()}"}"""
                    )
            ).andExpect(status().isBadRequest)
        }
    }

    @Nested
    inner class IncassoExecuteValidation {

        @Test
        fun `rejects a past expected incasso date with 400`() {
            val board = createUserWithRole(Role.BOARD)
            val period = createContributionPeriodFixture()
            val user = createUserWithRole(Role.MEMBER)
            val cutoff = LocalDate.now().toString()

            mvc.perform(
                post("/incassoNotifications/bulk/execute")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"userIds":[${user.id}],"contributionPeriodId":${period.id},"cutoffDate":"$cutoff","expectedIncassoDate":"${pastDate()}"}"""
                    )
            ).andExpect(status().isBadRequest)
        }
    }

    @Nested
    inner class PreviewValidation {

        @Test
        fun `preview rejects a past payment due date with 400`() {
            val board = createUserWithRole(Role.BOARD)
            val period = createContributionPeriodFixture()
            val user = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/contributionReminders/preview")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"userId":${user.id},"contributionPeriodId":${period.id},"feeType":"FULL_YEAR_FEE","paymentDueDate":"${pastDate()}"}"""
                    )
            ).andExpect(status().isBadRequest)
        }
    }
}
