package net.blueshell.api.platform.integration.cohort.application

import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.platform.integration.cohort.persistence.CohortFactKind
import net.blueshell.api.platform.integration.cohort.persistence.CohortSubjectType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Ensures the subjects + cohorts for a [ContributionPeriod] exist before the
 * engine evaluates a user against the period facts.
 *
 * Each period fans out to three subjects — `CONTRIBUTION_PAID`,
 * `MEMBER_IN_PERIOD`, `ACTIVE_IN_PERIOD` — each with one BREVO list cohort.
 * V67 backfilled these for existing periods; this resolver only does work for
 * periods created after cutover. A thin spec builder over
 * [CohortProvisioningService]; Brevo lists are created lazily on first ADD.
 */
@Service
class ContributionPeriodCohortResolver(
    private val provisioning: CohortProvisioningService,
    private val periods: ContributionPeriodService,
) {
    @Transactional
    fun materialize(periodId: Long) {
        val period = periods.findById(periodId)
        provisioning.provision(
            spec(CohortFactKind.CONTRIBUTION_PAID, CohortSubjectType.PERIOD_PAYERS, periodId, paidLabelFor(period)),
        )
        provisioning.provision(
            spec(CohortFactKind.MEMBER_IN_PERIOD, CohortSubjectType.PERIOD_MEMBERS, periodId, memberLabelFor(period)),
        )
        provisioning.provision(
            spec(CohortFactKind.ACTIVE_IN_PERIOD, CohortSubjectType.PERIOD_ACTIVE_MEMBERS, periodId, activeLabelFor(period)),
        )
    }

    private fun spec(factKind: CohortFactKind, subjectType: CohortSubjectType, periodId: Long, label: String) =
        CohortProvisioningSpec(
            factKind = factKind,
            factKey = periodId.toString(),
            subjectType = subjectType,
            label = label,
            folder = PERIOD_FOLDER,
        )

    companion object {
        const val PERIOD_FOLDER = "Periods"

        fun paidLabelFor(period: ContributionPeriod): String =
            "Contribution Paid ${period.startDate.year} - ${period.endDate.year}"

        fun memberLabelFor(period: ContributionPeriod): String =
            "Members ${period.startDate.year} - ${period.endDate.year}"

        fun activeLabelFor(period: ContributionPeriod): String =
            "Active Members ${period.startDate.year} - ${period.endDate.year}"
    }
}
