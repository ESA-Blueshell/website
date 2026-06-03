package net.blueshell.api.platform.integration.cohort.port.`in`

import net.blueshell.api.platform.integration.cohort.application.CohortMappingRow
import net.blueshell.api.platform.integration.sync.port.TargetSystem

/**
 * Inbound (driving) port: admin management of a subject's external
 * targets — linking an existing target, creating a fresh one, and
 * repointing a mapping at a different target.
 *
 * Driving adapters (the subjects controller, the delete-target job
 * handler) call this directly; the implementation in `cohort/application/`
 * owns the business logic and the transaction boundaries. External writes
 * happen through the [net.blueshell.api.platform.integration.cohort.port.out.CohortPort]
 * driven port; external removals after a switch are handed off to the
 * `cohort.delete-external-target` job rather than run inline.
 */
interface CohortTargeting {
    /**
     * Maps the subject's [system] cohort to an existing external target by
     * id. Fails with 409 when the subject already has an active mapping for
     * [system]. No external call — the id is trusted.
     */
    fun linkExisting(subjectId: Long, system: TargetSystem, externalId: String): CohortMappingRow

    /**
     * Creates a new external target on [system] (outside any DB
     * transaction) and maps the subject's [system] cohort to it. Fails with
     * 409 when the subject already has an active mapping for [system].
     */
    fun create(subjectId: Long, system: TargetSystem, label: String, folderHint: String?): CohortMappingRow

    /**
     * Repoints [cohortId]'s external mapping at [externalId], keeping the
     * same local `Cohort` row. Optionally enqueues `cohort.delete-external-
     * target` for the previous target and `cohort.reconcile-list` for the
     * new one.
     */
    fun switchTarget(cohortId: Long, externalId: String, deletePrevious: Boolean, reconcileNow: Boolean): CohortMappingRow

    /**
     * Deletes an external target. Driven by
     * [net.blueshell.api.platform.integration.cohort.adapter.job.DeleteExternalTargetJobHandler];
     * provider "already gone" is success.
     */
    fun deleteTarget(system: TargetSystem, externalTargetId: String)
}
