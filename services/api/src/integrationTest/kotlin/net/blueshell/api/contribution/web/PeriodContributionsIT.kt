package net.blueshell.api.contribution.web

import net.blueshell.api.contribution.api.ContributionPeriodService
import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

@SpringBootTest
class PeriodContributionsIT : UserTestSupport() {
    @Autowired
    private lateinit var periods: ContributionPeriodService

    @Test
    fun `the board reads a period's members and runs, and members cannot`() {
        val period =
            periods.create(
                ContributionPeriod(
                    startDate = LocalDate.of(2033, 9, 1),
                    endDate = LocalDate.of(2034, 8, 31),
                    halfYearCutoffDate = LocalDate.of(2034, 2, 1),
                ),
            )

        mvc
            .perform(get("/contributionPeriods/${period.id}/members").with(signedIn(createUserWithRole(Role.BOARD))))
            .andExpect(status().isOk)
        mvc
            .perform(get("/contributionPeriods/${period.id}/members").with(signedIn(createUserWithRole(Role.MEMBER))))
            .andExpect(status().isForbidden)
    }
}
