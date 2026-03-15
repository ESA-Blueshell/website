package net.blueshell.api.domain.contribution.web

import net.blueshell.api.domain.contribution.application.ContributionService
import net.blueshell.api.domain.contribution.persistence.Contribution
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
class ContributionControllerIT : UserTestSupport() {

    @Autowired
    private lateinit var contributionService: ContributionService

    private fun createPayload(userId: Long, periodId: Long): String =
        """{"userId":$userId,"contributionPeriodId":$periodId}"""

    @Nested
    inner class CreateContribution {

        @Test
        fun `creates contribution`() {
            val board = createUserWithRole(Role.BOARD)
            val user = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()

            mvc.perform(
                post("/contributions")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createPayload(user.id!!, period.id!!))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.userId").value(user.id))
                .andExpect(jsonPath("$.contributionPeriodId").value(period.id))
        }

        @Test
        fun `returns bad request for invalid payload`() {
            val board = createUserWithRole(Role.BOARD)
            val period = createContributionPeriodFixture()

            mvc.perform(
                post("/contributions")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"contributionPeriodId":${period.id}}""")
            )
                .andExpect(status().isBadRequest)
        }
    }

    @Nested
    inner class FindContributions {

        @Test
        fun `lists contributions for period`() {
            val board = createUserWithRole(Role.BOARD)
            val user = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            persist(
                Contribution(
                    id = Contribution.Id(user.id, period.id),
                    user = user,
                    contributionPeriod = period,
                )
            )

            mvc.perform(
                get("/contributions")
                    .with(bearer(board))
                    .param("contributionPeriodId", period.id!!.toString())
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$").isArray)
                .andExpect(jsonPath("$[0].userId").value(user.id))
                .andExpect(jsonPath("$[0].contributionPeriodId").value(period.id))
        }

        @Test
        fun `returns not found when period is unknown`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                get("/contributions")
                    .with(bearer(board))
                    .param("contributionPeriodId", "999999")
            )
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    inner class DeleteContribution {

        @Test
        fun `deletes contribution`() {
            val board = createUserWithRole(Role.BOARD)
            val user = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            persist(
                Contribution(
                    id = Contribution.Id(user.id, period.id),
                    user = user,
                    contributionPeriod = period,
                )
            )

            mvc.perform(
                delete("/contributionPeriods/{contributionPeriodId}/users/{userId}/contributions", period.id, user.id)
                    .with(bearer(board))
            )
                .andExpect(status().isNoContent)
        }

        @Test
        fun `soft-deleted contribution is not found by existsById`() {
            val board = createUserWithRole(Role.BOARD)
            val user = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            persist(
                Contribution(
                    id = Contribution.Id(user.id, period.id),
                    user = user,
                    contributionPeriod = period,
                )
            )

            mvc.perform(
                delete("/contributionPeriods/{contributionPeriodId}/users/{userId}/contributions", period.id, user.id)
                    .with(bearer(board))
            )
                .andExpect(status().isNoContent)

            val existsAfter = transactionTemplate.execute {
                entityManager.clear()
                contributionService.existsByUserIdAndPeriodId(user.id!!, period.id!!)
            }
            assertThat(existsAfter)
                .describedAs("Contribution should NOT exist after soft-delete")
                .isFalse()
        }

        @Test
        fun `returns not found when contribution is unknown`() {
            val board = createUserWithRole(Role.BOARD)
            val user = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()

            mvc.perform(
                delete("/contributionPeriods/{contributionPeriodId}/users/{userId}/contributions", period.id, user.id)
                    .with(bearer(board))
            )
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    inner class FindContributionsByPeriodId {

        @Test
        fun `lists contributions by period endpoint`() {
            val board = createUserWithRole(Role.BOARD)
            val user = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            persist(
                Contribution(
                    id = Contribution.Id(user.id, period.id),
                    user = user,
                    contributionPeriod = period,
                )
            )

            mvc.perform(
                get("/contributionPeriods/{periodId}/contributions", period.id)
                    .with(bearer(board))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$").isArray)
                .andExpect(jsonPath("$[0].userId").value(user.id))
                .andExpect(jsonPath("$[0].contributionPeriodId").value(period.id))
        }

        @Test
        fun `returns not found when period is unknown`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                get("/contributionPeriods/{periodId}/contributions", 999999)
                    .with(bearer(board))
            )
                .andExpect(status().isNotFound)
        }
    }
}
