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
    private fun contributionPayload(userId: Long, contributionPeriodId: Long): String =
        """{"userId":$userId,"contributionPeriodId":$contributionPeriodId}"""

    @Nested
    inner class CreateContribution {

        @Test
        fun `allows BOARD to create contributions`() {
            val board = createUserWithRole(Role.BOARD)
            val user = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()

            mvc.perform(
                post("/contributions")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(contributionPayload(user.id!!, period.id!!))
            )
                .andExpect(status().isCreated)
        }

        @Test
        fun `denies non-BOARD users from creating contributions`() {
            val member = createUserWithRole(Role.MEMBER)
            val user = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()

            mvc.perform(
                post("/contributions")
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(contributionPayload(user.id!!, period.id!!))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val user = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            mvc.perform(
                post("/contributions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(contributionPayload(user.id!!, period.id!!))
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class FindContributions {

        @Test
        fun `allows BOARD to list contributions`() {
            val board = createUserWithRole(Role.BOARD)
            val periodId = createContributionPeriodFixture().id!!

            mvc.perform(
                get("/contributions")
                    .param("contributionPeriodId", periodId.toString())
                    .with(bearer(board))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `denies non-BOARD users from listing contributions`() {
            val member = createUserWithRole(Role.MEMBER)
            val periodId = createContributionPeriodFixture().id!!

            mvc.perform(
                get("/contributions")
                    .param("contributionPeriodId", periodId.toString())
                    .with(bearer(member))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val periodId = createContributionPeriodFixture().id!!
            mvc.perform(
                get("/contributions")
                    .param("contributionPeriodId", periodId.toString())
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class DeleteContribution {

        @Test
        fun `allows BOARD to delete contributions`() {
            val board = createUserWithRole(Role.BOARD)
            val user = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            persist(
                net.blueshell.api.domain.contribution.persistence.Contribution(
                    id = net.blueshell.api.domain.contribution.persistence.Contribution.Id(user.id, period.id),
                    user = user,
                    contributionPeriod = period,
                )
            )

            mvc.perform(
                delete("/contributionPeriods/{contributionPeriodId}/users/{userId}/contributions", period.id!!, user.id!!)
                    .with(bearer(board))
            )
                .andExpect(status().isNoContent)
        }

        @Test
        fun `denies non-BOARD users from deleting contributions`() {
            val member = createUserWithRole(Role.MEMBER)
            val user = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            persist(
                net.blueshell.api.domain.contribution.persistence.Contribution(
                    id = net.blueshell.api.domain.contribution.persistence.Contribution.Id(user.id, period.id),
                    user = user,
                    contributionPeriod = period,
                )
            )

            mvc.perform(
                delete("/contributionPeriods/{contributionPeriodId}/users/{userId}/contributions", period.id!!, user.id!!)
                    .with(bearer(member))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val user = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()

            mvc.perform(
                delete("/contributionPeriods/{contributionPeriodId}/users/{userId}/contributions", period.id!!, user.id!!)
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class FindContributionsByPeriodId {

        @Test
        fun `allows BOARD to list contributions by period`() {
            val board = createUserWithRole(Role.BOARD)
            val periodId = createContributionPeriodFixture().id!!

            mvc.perform(
                get("/contributionPeriods/{periodId}/contributions", periodId)
                    .with(bearer(board))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `denies non-BOARD users from listing contributions by period`() {
            val member = createUserWithRole(Role.MEMBER)
            val periodId = createContributionPeriodFixture().id!!

            mvc.perform(
                get("/contributionPeriods/{periodId}/contributions", periodId)
                    .with(bearer(member))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val periodId = createContributionPeriodFixture().id!!

            mvc.perform(get("/contributionPeriods/{periodId}/contributions", periodId))
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
                get("/contributions")
                    .param("contributionPeriodId", periodId.toString())
                    .with(bearer(admin))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `COMMITTEE cannot access contribution endpoints`() {
            val committee = createUserWithRole(Role.COMMITTEE)
            val periodId = createContributionPeriodFixture().id!!

            mvc.perform(
                get("/contributions")
                    .param("contributionPeriodId", periodId.toString())
                    .with(bearer(committee))
            )
                .andExpect(status().isForbidden)
        }
    }
}
