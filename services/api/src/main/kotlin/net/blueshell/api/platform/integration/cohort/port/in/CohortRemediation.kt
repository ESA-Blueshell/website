package net.blueshell.api.platform.integration.cohort.port.`in`

import net.blueshell.api.platform.integration.sync.application.ExternalIdConflictException
import net.blueshell.api.platform.integration.sync.persistence.ExternalIdMapping
import net.blueshell.api.platform.integration.sync.port.TargetSystem

/**
 * Inbound port: operator-triggered remediation of external-system
 * membership drift.
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
     * Removes one member from the external target backing [cohortId].
     * Called by the [RemoveExternalMemberJobHandler]; the cohort must
     * be materialised (non-null externalCohortId) or a
     * [net.blueshell.api.shared.job.NonRetryableJobException] is thrown.
     */
    fun removeExternalMember(cohortId: Long, externalUserId: String)
}
