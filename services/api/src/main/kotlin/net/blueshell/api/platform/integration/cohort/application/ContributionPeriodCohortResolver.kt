package net.blueshell.api.platform.integration.cohort.application

import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.platform.integration.cohort.persistence.Cohort
import net.blueshell.api.platform.integration.cohort.persistence.CohortFactKind
import net.blueshell.api.platform.integration.cohort.persistence.CohortKind
import net.blueshell.api.platform.integration.cohort.persistence.CohortRule
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRuleRepository
import net.blueshell.api.platform.integration.sync.port.TargetSystem
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Ensures the cohort and rule for a [ContributionPeriod] exist before
 * the engine evaluates a user against `CONTRIBUTION_PAID` facts.
 *
 * Each period maps to one [Cohort] (`system = BREVO`, `kind = LIST`,
 * label `"Contribution Paid YYYY - YYYY"`) plus one [CohortRule]
 * `(CONTRIBUTION_PAID, "<periodId>", that-cohort)`. V67 backfilled
 * these for every existing period; this resolver only does work for
 * periods created after cutover — when the first `Contribution` is
 * recorded for a new period, the cohort listener calls
 * [materialize] before re-evaluating the user.
 *
 * The cohort's external counterpart on Brevo is **not** created here.
 * It is materialised lazily by `CohortMembershipSyncService` on the
 * first `ADD` for any user — keeping the resolver in the application
 * layer with no outbound-port dependency.
 */
@Service
class ContributionPeriodCohortResolver(
    private val cohorts: CohortRepository,
    private val cohortRules: CohortRuleRepository,
    private val periods: ContributionPeriodService,
) {
    @Transactional
    fun materialize(periodId: Long): Cohort {
        cohortRules.findAllByFactKindAndFactKeyAndEnabledTrue(
            CohortFactKind.CONTRIBUTION_PAID,
            periodId.toString(),
        ).firstOrNull { it.cohort.system == BREVO_SYSTEM }
            ?.let { return it.cohort }

        val period = periods.findById(periodId)
        val cohort = cohorts.save(
            Cohort(
                system = BREVO_SYSTEM,
                kind = CohortKind.LIST,
                label = labelFor(period),
            )
        )
        cohortRules.save(
            CohortRule(
                factKind = CohortFactKind.CONTRIBUTION_PAID,
                factKey = periodId.toString(),
                cohort = cohort,
            )
        )
        return cohort
    }

    companion object {
        private val BREVO_SYSTEM = TargetSystem.BREVO.name

        fun labelFor(period: ContributionPeriod): String =
            "Contribution Paid ${period.startDate.year} - ${period.endDate.year}"
    }
}
