package net.blueshell.api.cohort.domain

import net.blueshell.api.sync.api.ExternalIdConflictException
import net.blueshell.api.sync.persistence.ExternalIdMapping
import net.blueshell.api.shared.enums.TargetSystem

/**
 * Inbound port: operator-triggered and scheduled remediation of
 * external-system membership drift.
 */
interface CohortRemediation {
    /**
     * Links [externalUserId] on [system] to [userId] for subject [subjectId]. Idempotent for the
     * same triple, and raises [ExternalIdConflictException] where the external id is somebody
     * else's. A matching stranger row may be folded into the desired row, so the next drift read
     * reflects the claim.
     */
    fun linkUser(subjectId: Long, userId: Long, system: TargetSystem, externalUserId: String): ExternalIdMapping

    /**
     * Removes one member from the external target backing [cohortId]
     * and soft-deletes the corresponding stranger row from the
     * [net.blueshell.api.cohort.persistence.CohortMember]
     * ledger. Called by [net.blueshell.api.cohort.domain.RemoveExternalMemberJobHandler].
     */
    fun removeExternalMember(cohortId: Long, externalUserId: String)

    /**
     * Verifies [cohortId] against its live external member list: confirms
     * present members, demotes vanished ones, records strangers, and
     * enqueues follow-up ADD/contact jobs for discrepancies. The
     * per-member sync path establishes health; this only verifies it.
     * Called by [net.blueshell.api.cohort.domain.ReconcileListJobHandler].
     */
    fun verifyCohort(cohortId: Long)

    /**
     * Operator-triggered repair for a bound cohort after a target has been
     * linked manually. Re-enqueues ADD jobs for desired rows that are not
     * currently synced so no-op rule evaluation does not strand them.
     */
    fun repairMissingAdds(cohortId: Long): CohortRepairResult
}

data class CohortRepairResult(val cohortId: Long, val enqueuedAdds: Int)
