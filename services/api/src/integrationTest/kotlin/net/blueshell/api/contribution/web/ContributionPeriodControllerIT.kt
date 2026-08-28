package net.blueshell.api.contribution.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

@SpringBootTest
class ContributionPeriodControllerIT : UserTestSupport() {

    private fun createPayload(
        startDate: LocalDate,
        endDate: LocalDate,
        halfYearFee: Double = 25.0,
        fullYearFee: Double = 45.0,
        alumniFee: Double = 10.0
    ): String =
        """{"startDate":"$startDate","endDate":"$endDate","halfYearFee":$halfYearFee,"fullYearFee":$fullYearFee,"alumniFee":$alumniFee}"""

    private fun updatePayload(
        version: Long,
        startDate: LocalDate,
        endDate: LocalDate,
        halfYearFee: Double = 30.0,
        fullYearFee: Double = 50.0,
        alumniFee: Double = 12.5
    ): String =
        """{"startDate":"$startDate","endDate":"$endDate","halfYearFee":$halfYearFee,"fullYearFee":$fullYearFee,"alumniFee":$alumniFee,"version":$version}"""

    @Nested
    inner class FindContributionPeriods {

        @Test
        fun `lists contribution periods`() {
            createContributionPeriodFixture(
                startDate = LocalDate.now().minusDays(5),
                endDate = LocalDate.now().plusDays(5)
            )

            mvc.perform(get("/contributionPeriods"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$").isArray)
                .andExpect(jsonPath("$[0].id").isNotEmpty)
        }
    }

    @Nested
    inner class FindCurrentContributionPeriod {

        @Test
        fun `returns current contribution period`() {
            val current = createContributionPeriodFixture(
                startDate = LocalDate.now().minusDays(5),
                endDate = LocalDate.now().plusDays(5)
            )

            mvc.perform(get("/contributionPeriods/current"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(current.id))
                .andExpect(jsonPath("$.startDate").value(current.startDate.toString()))
                .andExpect(jsonPath("$.endDate").value(current.endDate.toString()))
        }

        @Test
        fun `returns no content when no contribution period exists`() {
            mvc.perform(get("/contributionPeriods/current"))
                .andExpect(status().isNoContent)
        }
    }

    @Nested
    inner class CreateContributionPeriod {

        @Test
        fun `creates contribution period`() {
            val board = createUserWithRole(Role.BOARD)
            val startDate = LocalDate.now().minusDays(1)
            val endDate = LocalDate.now().plusDays(60)

            mvc.perform(
                post("/contributionPeriods")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createPayload(startDate, endDate))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.id").isNotEmpty)
                .andExpect(jsonPath("$.startDate").value(startDate.toString()))
                .andExpect(jsonPath("$.endDate").value(endDate.toString()))
        }

        @Test
        fun `returns bad request for invalid payload`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                post("/contributionPeriods")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        createPayload(
                            startDate = LocalDate.now().minusDays(1),
                            endDate = LocalDate.now().minusDays(2)
                        )
                    )
            )
                .andExpect(status().isBadRequest)
        }
    }

    @Nested
    inner class UpdateContributionPeriod {

        @Test
        fun `updates contribution period`() {
            val board = createUserWithRole(Role.BOARD)
            val period = createContributionPeriodFixture(
                startDate = LocalDate.now().minusDays(1),
                endDate = LocalDate.now().plusDays(60)
            )
            val newStartDate = period.startDate.minusDays(7)
            val newEndDate = period.endDate.plusDays(30)

            mvc.perform(
                put("/contributionPeriods/{id}", period.id)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updatePayload(period.version, newStartDate, newEndDate))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(period.id))
                .andExpect(jsonPath("$.startDate").value(newStartDate.toString()))
                .andExpect(jsonPath("$.endDate").value(newEndDate.toString()))
                .andExpect(jsonPath("$.halfYearFee").value(30.0))
                .andExpect(jsonPath("$.fullYearFee").value(50.0))
                .andExpect(jsonPath("$.alumniFee").value(12.5))
        }

        @Test
        fun `returns not found for unknown period`() {
            val board = createUserWithRole(Role.BOARD)
            val startDate = LocalDate.now().minusDays(1)
            val endDate = LocalDate.now().plusDays(10)

            mvc.perform(
                put("/contributionPeriods/{id}", 999999)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updatePayload(0, startDate, endDate))
            )
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    inner class DeleteContributionPeriodById {

        @Test
        fun `deletes contribution period`() {
            val board = createUserWithRole(Role.BOARD)
            val period = createContributionPeriodFixture()

            mvc.perform(
                delete("/contributionPeriods/{id}", period.id)
                    .with(bearer(board))
            )
                .andExpect(status().isNoContent)
        }

        @Test
        fun `returns not found for unknown period`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                delete("/contributionPeriods/{id}", 999999)
                    .with(bearer(board))
            )
                .andExpect(status().isNotFound)
        }
    }
}
