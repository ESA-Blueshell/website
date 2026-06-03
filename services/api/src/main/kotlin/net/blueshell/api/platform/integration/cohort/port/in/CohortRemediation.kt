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
     * external-id mapping table. Idempotent for the same triple;
     * throws [ExternalIdConflictException] → 409 if the external id
     * belongs to a different user.
     */
    fun linkUser(userId: Long, system: TargetSystem, externalUserId: String): ExternalIdMapping

    /**
     * Removes one member from the external target backing [cohortId]
     * and deletes the corresponding shadow row so the drift panel
     * reflects the change without waiting for the next reconcile run.
     * Called by [net.blueshell.api.platform.integration.cohort.adapter.job.RemoveExternalMemberJobHandler].
     */
    fun removeExternalMember(cohortId: Long, externalUserId: String)

    /**
     * Fetches the full external member list for [cohortId], updates the
     * shadow table, and enqueues ADD/REMOVE jobs for each discrepancy.
     * Called by [net.blueshell.api.platform.integration.cohort.adapter.job.ReconcileListJobHandler].
     */
    fun reconcileList(cohortId: Long)
}
