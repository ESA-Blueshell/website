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
 * Security tests for ContributionController.
 *
 * Verifies authorization rules are correctly enforced per ADR-014:
 * - BOARD users can perform all contribution operations (CRUD)
 * - Non-BOARD users cannot access any contribution endpoints
 */
@SpringBootTest
class ContributionControllerSecurityTest : UserTestSupport() {

    @Nested
    inner class CreateContribution {

        @Test
        fun `allows BOARD to create contributions`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                post("/contributions")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userId":1,"contributionPeriodId":1,"amount":100}""")
            )
                .andExpect(status().isCreated)
        }

        @Test
        fun `denies non-BOARD users from creating contributions`() {
            val member = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/contributions")
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userId":1,"contributionPeriodId":1,"amount":100}""")
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            mvc.perform(
                post("/contributions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userId":1,"contributionPeriodId":1,"amount":100}""")
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class FindContributions {

        @Test
        fun `allows BOARD to list contributions`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                get("/contributions")
                    .param("contributionPeriodId", "1")
                    .with(bearer(board))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `denies non-BOARD users from listing contributions`() {
            val member = createUserWithRole(Role.MEMBER)

            mvc.perform(
                get("/contributions")
                    .param("contributionPeriodId", "1")
                    .with(bearer(member))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            mvc.perform(
                get("/contributions")
                    .param("contributionPeriodId", "1")
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class DeleteContribution {

        @Test
        fun `allows BOARD to delete contributions`() {
            val board = createUserWithRole(Role.BOARD)
            val userId = 1L
            val periodId = 1L

            mvc.perform(
                delete("/contributionPeriods/{contributionPeriodId}/users/{userId}/contributions", periodId, userId)
                    .with(bearer(board))
            )
                .andExpect(status().isNoContent)
        }

        @Test
        fun `denies non-BOARD users from deleting contributions`() {
            val member = createUserWithRole(Role.MEMBER)
            val userId = 1L
            val periodId = 1L

            mvc.perform(
                delete("/contributionPeriods/{contributionPeriodId}/users/{userId}/contributions", periodId, userId)
                    .with(bearer(member))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val userId = 1L
            val periodId = 1L

            mvc.perform(
                delete("/contributionPeriods/{contributionPeriodId}/users/{userId}/contributions", periodId, userId)
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class FindContributionsByPeriodId {

        @Test
        fun `allows BOARD to list contributions by period`() {
            val board = createUserWithRole(Role.BOARD)
            val periodId = 1L

            mvc.perform(
                get("/contributionPeriods/{periodId}/contributions", periodId)
                    .with(bearer(board))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `denies non-BOARD users from listing contributions by period`() {
            val member = createUserWithRole(Role.MEMBER)
            val periodId = 1L

            mvc.perform(
                get("/contributionPeriods/{periodId}/contributions", periodId)
                    .with(bearer(member))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val periodId = 1L

            mvc.perform(get("/contributionPeriods/{periodId}/contributions", periodId))
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class RoleHierarchy {

        @Test
        fun `ADMIN can perform BOARD operations`() {
            val admin = createUserWithRole(Role.ADMIN)

            mvc.perform(
                get("/contributions")
                    .param("contributionPeriodId", "1")
                    .with(bearer(admin))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `COMMITTEE cannot access contribution endpoints`() {
            val committee = createUserWithRole(Role.COMMITTEE)

            mvc.perform(
                get("/contributions")
                    .param("contributionPeriodId", "1")
                    .with(bearer(committee))
            )
                .andExpect(status().isForbidden)
        }
    }
}
