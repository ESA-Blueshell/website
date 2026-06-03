package net.blueshell.api.platform.integration.cohort.port.`in`

import net.blueshell.api.platform.integration.sync.application.ExternalIdConflictException
import net.blueshell.api.platform.integration.sync.persistence.ExternalIdMapping
import net.blueshell.api.platform.integration.sync.port.TargetSystem

/**
 * Inbound port: operator-triggered and scheduled remediation of
 * external-system membership drift.
 */
interface CohortRemediation {
    /**
     * Links [externalUserId] on [system] to [userId] in the local
     * external-id mapping table for the subject mapping [subjectId].
     * Idempotent for the same triple; throws [ExternalIdConflictException]
     * → 409 if the external id belongs to a different user.
     *
     * When the subject/system ledger already has both a desired row and
     * a matching stranger row, implementations may fold the stranger into
     * the desired row locally so the next drift read reflects the claim.
     */
    fun linkUser(subjectId: Long, userId: Long, system: TargetSystem, externalUserId: String): ExternalIdMapping

    /**
     * Removes one member from the external target backing [cohortId]
     * and soft-deletes the corresponding stranger row from the
     * [net.blueshell.api.platform.integration.cohort.persistence.CohortMember]
     * ledger. Called by [net.blueshell.api.platform.integration.cohort.adapter.job.RemoveExternalMemberJobHandler].
     */
    fun removeExternalMember(cohortId: Long, externalUserId: String)

    /**
     * Fetches the full external member list for [cohortId], updates the
     * unified [net.blueshell.api.platform.integration.cohort.persistence.CohortMember]
     * ledger, and enqueues ADD jobs for each discrepancy.
     * Called by [net.blueshell.api.platform.integration.cohort.adapter.job.ReconcileListJobHandler].
     */
    fun reconcileList(cohortId: Long)
}
