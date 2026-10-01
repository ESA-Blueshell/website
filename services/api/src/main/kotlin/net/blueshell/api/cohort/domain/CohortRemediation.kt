package net.blueshell.api.cohort.domain

import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.sync.api.ExternalIdConflictException
import net.blueshell.api.sync.persistence.ExternalIdMapping

/**
 * Operator-triggered and scheduled remediation of external-system membership drift. An
 * interface because `CohortController` and `CohortController` are written against it —
 * the module publishes this surface to its own web layer.
 */
interface CohortRemediation {
    /**
     * Links [externalUserId] on [system] to [userId] for cohort [cohortId]. Idempotent for the
     * same triple, and raises [ExternalIdConflictException] where the external id is somebody
     * else's. A matching stranger row may be folded into the desired row, so the next drift read
     * reflects the claim.
     */
    fun linkUser(
        cohortId: Long,
        userId: Long,
        system: TargetSystem,
        externalUserId: String,
    ): ExternalIdMapping

    /**
     * Removes one member from the external target backing [targetId]
     * and soft-deletes the corresponding stranger row from the
     * [net.blueshell.api.cohort.persistence.TargetMember]
     * ledger. Run by the `cohort.remove-external-member` job. Answers why nothing was removed,
     * or null where it was.
     */
    fun removeExternalMember(
        targetId: Long,
        externalUserId: String,
    ): String?

    /**
     * Verifies [targetId] against its live external member list: confirms
     * present members, demotes vanished ones, records strangers, and
     * enqueues follow-up ADD/contact jobs for discrepancies. The
     * per-member sync path establishes health; this only verifies it.
     * Run by the `cohort.reconcile-list` job, which records each run's drift with [trigger].
     * Answers why nothing was compared, or null where it was.
     */
    fun verifyTarget(
        targetId: Long,
        trigger: JobTrigger?,
    ): String?
}
