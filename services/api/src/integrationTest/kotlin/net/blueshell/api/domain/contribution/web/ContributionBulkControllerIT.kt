package net.blueshell.api.domain.contribution.web

import net.blueshell.api.domain.contribution.application.ContributionService
import net.blueshell.api.domain.contribution.persistence.Contribution
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Contract + authz for the execute-only mark-paid / mark-unpaid bulk endpoints. Preview
 * for these actions is computed frontend-side, so there is no server preview endpoint to
 * test; the redesign's regression net is the shared decide() unit tests plus the
 * preview==execute invariants on the reminder/incasso/resume ITs.
 * See docs/proposals/bulk-actions/REDESIGN.md §2 & §7.
 */
@SpringBootTest
class ContributionBulkControllerIT : UserTestSupport() {

    @Autowired
    private lateinit var contributionService: ContributionService

    private fun markPaid(user: User, period: ContributionPeriod) = persist(
        Contribution(id = Contribution.Id(user.id, period.id), user = user, contributionPeriod = period)
    )

    private fun body(userIds: List<Long>, periodId: Long) =
        """{"userIds":[${userIds.joinToString(",")}],"contributionPeriodId":$periodId}"""

    @Nested
    inner class Execute {

        @Test
        fun `mark-paid creates contributions only for unpaid users`() {
            val board = createUserWithRole(Role.BOARD)
            val a = createUserWithRole(Role.MEMBER)
            val b = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            markPaid(a, period)

            mvc.perform(
                post("/contributions/bulk/mark-paid")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(a.id!!, b.id!!), period.id!!))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.applied").value(1))
                .andExpect(jsonPath("$.skipped").value(1))

            val bothPaid = transactionTemplate.execute {
                entityManager.clear()
                contributionService.existsByUserIdAndPeriodId(a.id!!, period.id!!) &&
                    contributionService.existsByUserIdAndPeriodId(b.id!!, period.id!!)
            }
            assertThat(bothPaid).isTrue()
        }

        @Test
        fun `mark-unpaid deletes existing contributions only`() {
            val board = createUserWithRole(Role.BOARD)
            val a = createUserWithRole(Role.MEMBER)
            val b = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            markPaid(a, period)

            mvc.perform(
                post("/contributions/bulk/mark-unpaid")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(a.id!!, b.id!!), period.id!!))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.applied").value(1))
                .andExpect(jsonPath("$.skipped").value(1))

            val aStillPaid = transactionTemplate.execute {
                entityManager.clear()
                contributionService.existsByUserIdAndPeriodId(a.id!!, period.id!!)
            }
            assertThat(aStillPaid).isFalse()
        }

        @Test
        fun `rejects empty selection`() {
            val board = createUserWithRole(Role.BOARD)
            val period = createContributionPeriodFixture()

            mvc.perform(
                post("/contributions/bulk/mark-paid")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userIds":[],"contributionPeriodId":${period.id}}""")
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `returns not found when period is unknown`() {
            val board = createUserWithRole(Role.BOARD)
            val user = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/contributions/bulk/mark-paid")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(user.id!!), 999999))
            )
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    inner class Authorization {

        @Test
        fun `non-board is forbidden`() {
            val member = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()

            mvc.perform(
                post("/contributions/bulk/mark-paid")
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(member.id!!), period.id!!))
            )
                .andExpect(status().isForbidden)
        }
    }
}
