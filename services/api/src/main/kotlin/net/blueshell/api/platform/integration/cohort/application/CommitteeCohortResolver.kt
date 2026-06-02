package net.blueshell.api.platform.integration.cohort.application

import net.blueshell.api.domain.committee.application.CommitteeService
import net.blueshell.api.domain.committee.persistence.Committee
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
 * Ensures the cohort and rule for a [Committee] exist before the engine
 * evaluates a user against `COMMITTEE` facts.
 *
 * One committee maps to one [Cohort] (`system = BREVO`, `kind = LIST`,
 * label = committee name) plus one [CohortRule]
 * `(COMMITTEE, "<committeeId>", that-cohort)`.
 *
 * V69 backfills the cohort + rule rows for every existing committee.
 * This resolver only does work for committees created post-V69, when
 * the first `CommitteeMembershipChanged` event arrives — the listener
 * calls [materialize] before re-evaluating the user.
 *
 * The cohort's Brevo list is created lazily by `CohortMembershipSyncService`
 * on the first `ADD` for any user, keeping this resolver in the application
 * layer with no outbound-port dependency (mirrors
 * [ContributionPeriodCohortResolver]).
 */
@Service
class CommitteeCohortResolver(
    private val cohorts: CohortRepository,
    private val cohortRules: CohortRuleRepository,
    private val committees: CommitteeService,
) {
    @Transactional
    fun materialize(committeeId: Long): Cohort {
        cohortRules.findAllByFactKindAndFactKeyAndEnabledTrue(
            CohortFactKind.COMMITTEE,
            committeeId.toString(),
        ).firstOrNull { it.cohort.system == BREVO_SYSTEM }
            ?.let { return it.cohort }

        val committee = committees.findById(committeeId)
        val cohort = cohorts.save(
            Cohort(
                system = BREVO_SYSTEM,
                kind = CohortKind.LIST,
                label = labelFor(committee),
                folder = COMMITTEE_FOLDER,
            )
        )
        cohortRules.save(
            CohortRule(
                factKind = CohortFactKind.COMMITTEE,
                factKey = committeeId.toString(),
                cohort = cohort,
            )
        )
        return cohort
    }

    companion object {
        private val BREVO_SYSTEM = TargetSystem.BREVO.name
        const val COMMITTEE_FOLDER = "Committees"

        fun labelFor(committee: Committee): String = committee.name
    }
}
