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
 * Security tests for ContributionPeriodController.
 *
 * Verifies authorization rules are correctly enforced per ADR-014:
 * - Anyone can list contribution periods and get current period
 * - BOARD users can create/update/delete periods
 * - Non-BOARD users cannot modify periods
 */
@SpringBootTest
class ContributionPeriodControllerSecurityTest : UserTestSupport() {
    private fun contributionPeriodPayload(
        startDate: String = "2026-01-01",
        endDate: String = "2026-12-31"
    ): String =
        """{"startDate":"$startDate","endDate":"$endDate","halfYearFee":25.0,"fullYearFee":45.0,"alumniFee":10.0}"""

    private fun updateContributionPeriodPayload(
        version: Long,
        startDate: String = "2026-01-01",
        endDate: String = "2026-12-31"
    ): String =
        """{"startDate":"$startDate","endDate":"$endDate","halfYearFee":25.0,"fullYearFee":45.0,"alumniFee":10.0,"version":$version}"""

    @Nested
    inner class FindContributionPeriods {

        @Test
        fun `allows anyone to list contribution periods`() {
            mvc.perform(get("/contributionPeriods"))
                .andExpect(status().isOk)
        }

        @Test
        fun `allows authenticated user to list periods`() {
            val member = createUserWithRole(Role.MEMBER)

            mvc.perform(
                get("/contributionPeriods")
                    .with(bearer(member))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows BOARD to list periods`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                get("/contributionPeriods")
                    .with(bearer(board))
            )
                .andExpect(status().isOk)
        }
    }

    @Nested
    inner class FindCurrentContributionPeriod {

        @Test
        fun `returns no content when no current period exists`() {
            mvc.perform(get("/contributionPeriods/current"))
                .andExpect(status().isNoContent)
        }

        @Test
        fun `allows anyone to get current period`() {
            createContributionPeriodFixture()
            mvc.perform(get("/contributionPeriods/current"))
                .andExpect(status().isOk)
        }

        @Test
        fun `allows authenticated user to get current period`() {
            val member = createUserWithRole(Role.MEMBER)
            createContributionPeriodFixture()

            mvc.perform(
                get("/contributionPeriods/current")
                    .with(bearer(member))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows unauthenticated access to current period`() {
            createContributionPeriodFixture()
            mvc.perform(get("/contributionPeriods/current"))
                .andExpect(status().isOk)
        }
    }

    @Nested
    inner class CreateContributionPeriod {

        @Test
        fun `allows BOARD to create periods`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                post("/contributionPeriods")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(contributionPeriodPayload())
            )
                .andExpect(status().isCreated)
        }

        @Test
        fun `denies non-BOARD users from creating periods`() {
            val member = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/contributionPeriods")
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(contributionPeriodPayload())
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            mvc.perform(
                post("/contributionPeriods")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(contributionPeriodPayload())
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class UpdateContributionPeriod {

        @Test
        fun `allows BOARD to update periods`() {
            val board = createUserWithRole(Role.BOARD)
            val period = createContributionPeriodFixture()
            val periodId = period.id!!

            mvc.perform(
                put("/contributionPeriods/{id}", periodId)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateContributionPeriodPayload(period.version))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `denies non-BOARD users from updating periods`() {
            val member = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            val periodId = period.id!!

            mvc.perform(
                put("/contributionPeriods/{id}", periodId)
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateContributionPeriodPayload(period.version))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val period = createContributionPeriodFixture()
            val periodId = period.id!!

            mvc.perform(
                put("/contributionPeriods/{id}", periodId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateContributionPeriodPayload(period.version))
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class DeleteContributionPeriod {

        @Test
        fun `allows BOARD to delete periods`() {
            val board = createUserWithRole(Role.BOARD)
            val periodId = createContributionPeriodFixture().id!!

            mvc.perform(
                delete("/contributionPeriods/{id}", periodId)
                    .with(bearer(board))
            )
                .andExpect(status().isNoContent)
        }

        @Test
        fun `denies non-BOARD users from deleting periods`() {
            val member = createUserWithRole(Role.MEMBER)
            val periodId = createContributionPeriodFixture().id!!

            mvc.perform(
                delete("/contributionPeriods/{id}", periodId)
                    .with(bearer(member))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val periodId = createContributionPeriodFixture().id!!

            mvc.perform(delete("/contributionPeriods/{id}", periodId))
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class RoleHierarchy {

        @Test
        fun `ADMIN can perform BOARD operations`() {
            val admin = createUserWithRole(Role.ADMIN)

            mvc.perform(
                post("/contributionPeriods")
                    .with(bearer(admin))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(contributionPeriodPayload())
            )
                .andExpect(status().isCreated)
        }
    }
}
