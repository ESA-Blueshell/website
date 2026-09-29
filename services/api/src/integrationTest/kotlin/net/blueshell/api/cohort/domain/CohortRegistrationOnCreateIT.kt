package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortSubjectRepository
import net.blueshell.api.cohort.persistence.CohortSubjectType
import net.blueshell.api.committee.api.CommitteeService
import net.blueshell.api.contribution.api.ContributionPeriodService
import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Duration
import java.time.LocalDate

/** Creating a contribution period or a committee registers its cohorts straight away. */
@SpringBootTest
class CohortRegistrationOnCreateIT : UserTestSupport() {
    @Autowired
    private lateinit var periods: ContributionPeriodService

    @Autowired
    private lateinit var committees: CommitteeService

    @Autowired
    private lateinit var subjects: CohortSubjectRepository

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

        listOf(CohortSubjectType.PERIOD_PAYERS, CohortSubjectType.PERIOD_MEMBERS, CohortSubjectType.PERIOD_ACTIVE_MEMBERS)
            .forEach { type ->
                assertThat(subjects.findByDefinitionKey("$type:${period.id}"))
                    .describedAs("no %s cohort for the new period", type)
                    .isNotNull
            }
    }

    @Test
    fun `a new committee with no members yet has its cohort`() {
        val committee = committees.createWithMembers("Registratiecie", "Checks its cohort", emptyList())

        assertThat(subjects.findByDefinitionKey("${CohortSubjectType.COMMITTEE_MEMBERS}:${committee.id}")).isNotNull
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

        val keys = subjects.findAll().mapNotNull { it.definitionKey }.filter { it.endsWith(":${period.id}") }
        assertThat(keys).doesNotHaveDuplicates().hasSize(3)
    }

    @Test
    fun `a new committee's list is created and linked by its create-target job`() {
        val committee = committees.createWithMembers("Lijstcie", "Gets its list", emptyList())
        val subject = subjects.findByDefinitionKey("${CohortSubjectType.COMMITTEE_MEMBERS}:${committee.id}")!!

        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100)).untilAsserted {
            val target = cohorts.findAllBySubjectId(subject.id!!).single()
            assertThat(target.externalId).isNotBlank()
            assertThat(target.folder).isEqualTo("Committees")
        }
    }
}
