package net.blueshell.api.platform.integration.cohort.application

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.platform.integration.cohort.persistence.CohortFactKind
import net.blueshell.api.platform.integration.cohort.persistence.CohortSubjectType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ContributionPeriodCohortResolverTest {

    private val provisioning: CohortProvisioningService = mockk(relaxed = true)
    private val periods: ContributionPeriodService = mockk()
    private val resolver = ContributionPeriodCohortResolver(provisioning, periods)

    @Test
    fun `materialize provisions the three period subjects for one period`() {
        val period = mockk<ContributionPeriod> {
            every { startDate } returns LocalDate.of(2024, 9, 1)
            every { endDate } returns LocalDate.of(2025, 8, 31)
        }
        every { periods.findById(3L) } returns period
        val specs = mutableListOf<CohortProvisioningSpec>()
        every { provisioning.provision(capture(specs)) } returns CohortProvisioningResult.Ready(mockk())

        resolver.materialize(3L)

        verify(exactly = 3) { provisioning.provision(any()) }
        assertThat(specs.map { it.factKind }).containsExactly(
            CohortFactKind.CONTRIBUTION_PAID,
            CohortFactKind.MEMBER_IN_PERIOD,
            CohortFactKind.ACTIVE_IN_PERIOD,
        )
        assertThat(specs.map { it.subjectType }).containsExactly(
            CohortSubjectType.PERIOD_PAYERS,
            CohortSubjectType.PERIOD_MEMBERS,
            CohortSubjectType.PERIOD_ACTIVE_MEMBERS,
        )
        assertThat(specs).allMatch { it.factKey == "3" && it.folder == "Periods" }
        assertThat(specs.map { it.label }).containsExactly(
            "Contribution Paid 2024 - 2025",
            "Members 2024 - 2025",
            "Active Members 2024 - 2025",
        )
    }
}
