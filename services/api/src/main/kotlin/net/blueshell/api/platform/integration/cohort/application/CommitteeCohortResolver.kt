package net.blueshell.api.platform.integration.cohort.application

import net.blueshell.api.domain.committee.application.CommitteeService
import net.blueshell.api.domain.committee.persistence.Committee
import net.blueshell.api.platform.integration.cohort.persistence.CohortFactKind
import net.blueshell.api.platform.integration.cohort.persistence.CohortSubjectType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Ensures the subject + cohort for a [Committee] exist before the engine
 * evaluates a user against `COMMITTEE` facts.
 *
 * One committee maps to one subject `(COMMITTEE, "<committeeId>")` and one
 * BREVO list cohort under it. V69 backfilled these for existing committees;
 * this resolver only does work for committees created post-V69, when the
 * first `CommitteeMembershipChanged` event arrives. It is a thin spec builder
 * over [CohortProvisioningService]; the Brevo list itself is created lazily by
 * the `cohort.materialize-target` job on the first ADD.
 */
@Service
class CommitteeCohortResolver(
    private val provisioning: CohortProvisioningService,
    private val committees: CommitteeService,
) {
    @Transactional
    fun materialize(committeeId: Long): CohortProvisioningResult {
        val committee = committees.findById(committeeId)
        return provisioning.provision(
            CohortProvisioningSpec(
                factKind = CohortFactKind.COMMITTEE,
                factKey = committeeId.toString(),
                subjectType = CohortSubjectType.COMMITTEE_MEMBERS,
                label = labelFor(committee),
                folder = COMMITTEE_FOLDER,
            ),
        )
    }

    companion object {
        const val COMMITTEE_FOLDER = "Committees"

        fun labelFor(committee: Committee): String = committee.name
    }
}
