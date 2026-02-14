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

    @Nested
    inner class SendContributionReminder {

        @Test
        fun `allows BOARD to send reminders`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                post("/contributionReminders")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"contributionPeriodId":1,"userId":1}""")
            )
                .andExpect(status().isCreated)
        }

        @Test
        fun `denies non-BOARD users from sending reminders`() {
            val member = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/contributionReminders")
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"contributionPeriodId":1,"userId":1}""")
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            mvc.perform(
                post("/contributionReminders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"contributionPeriodId":1,"userId":1}""")
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class SendContributionReminderBatch {

        @Test
        fun `allows BOARD to send batch reminders`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                post("/contributionReminders/batch")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""[{"contributionPeriodId":1,"userId":1},{"contributionPeriodId":1,"userId":2}]""")
            )
                .andExpect(status().isCreated)
        }

        @Test
        fun `denies non-BOARD users from sending batch reminders`() {
            val member = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/contributionReminders/batch")
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""[{"contributionPeriodId":1,"userId":1}]""")
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            mvc.perform(
                post("/contributionReminders/batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""[{"contributionPeriodId":1,"userId":1}]""")
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class FindContributionReminders {

        @Test
        fun `allows BOARD to list reminders`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                get("/contributionReminders")
                    .param("contributionPeriodId", "1")
                    .with(bearer(board))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `denies non-BOARD users from listing reminders`() {
            val member = createUserWithRole(Role.MEMBER)

            mvc.perform(
                get("/contributionReminders")
                    .param("contributionPeriodId", "1")
                    .with(bearer(member))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            mvc.perform(
                get("/contributionReminders")
                    .param("contributionPeriodId", "1")
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class RoleHierarchy {

        @Test
        fun `ADMIN can perform BOARD operations`() {
            val admin = createUserWithRole(Role.ADMIN)

            mvc.perform(
                get("/contributionReminders")
                    .param("contributionPeriodId", "1")
                    .with(bearer(admin))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `COMMITTEE cannot access reminder endpoints`() {
            val committee = createUserWithRole(Role.COMMITTEE)

            mvc.perform(
                get("/contributionReminders")
                    .param("contributionPeriodId", "1")
                    .with(bearer(committee))
            )
                .andExpect(status().isForbidden)
        }
    }
}
