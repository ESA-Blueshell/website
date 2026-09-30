package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.committee.api.CommitteeService
import net.blueshell.api.contribution.api.ContributionPeriodService
import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate

/** Creating a contribution period or a committee registers its cohorts straight away. */
@SpringBootTest
class CohortRegistrationOnCreateIT : UserTestSupport() {
    @Autowired
    private lateinit var periods: ContributionPeriodService

    @Autowired
    private lateinit var committees: CommitteeService

    @Autowired
    private lateinit var cohorts: CohortRepository

    @Test
    fun `a new contribution period has its three cohorts before any contribution exists`() {
        val period =
            periods.create(
                ContributionPeriod(
                    startDate = LocalDate.of(2031, 9, 1),
                    endDate = LocalDate.of(2032, 8, 31),
                    halfYearCutoffDate = LocalDate.of(2032, 2, 1),
                ),
            )

        listOf(CohortType.PERIOD_PAYERS, CohortType.PERIOD_MEMBERS, CohortType.PERIOD_ACTIVE_MEMBERS)
            .forEach { type ->
                assertThat(cohorts.findByDefinitionKey("$type:${period.id}"))
                    .describedAs("no %s cohort for the new period", type)
                    .isNotNull
            }
    }

    @Test
    fun `a new committee with no members yet has its cohort`() {
        val committee = committees.createWithMembers("Registratiecie", "Checks its cohort", emptyList())

        assertThat(cohorts.findByDefinitionKey("${CohortType.COMMITTEE_MEMBERS}:${committee.id}")).isNotNull
    }

    @Test
    fun `saving a period again adds no second cohort`() {
        val period =
            periods.create(
                ContributionPeriod(
                    startDate = LocalDate.of(2033, 9, 1),
                    endDate = LocalDate.of(2034, 8, 31),
                    halfYearCutoffDate = LocalDate.of(2034, 2, 1),
                ),
            )
        periods.update(period)

        val keys = cohorts.findAll().mapNotNull { it.definitionKey }.filter { it.endsWith(":${period.id}") }
        assertThat(keys).doesNotHaveDuplicates().hasSize(3)
    }
}
