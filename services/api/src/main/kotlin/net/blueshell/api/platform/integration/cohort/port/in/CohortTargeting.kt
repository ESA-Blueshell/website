package net.blueshell.api.platform.integration.cohort.port.`in`

import net.blueshell.api.platform.integration.cohort.application.CohortMappingRow
import net.blueshell.api.shared.enums.TargetSystem

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
     * [system] with a target id; an existing unbound row is filled in place.
     * No external call — the id is trusted.
     */
    fun linkExisting(subjectId: Long, system: TargetSystem, externalId: String): CohortMappingRow

    /**
     * Creates a new external target on [system] (outside any DB
     * transaction) and maps the subject's [system] cohort to it. Fails with
     * 409 when the subject already has an active mapping for [system].
     */
    fun create(subjectId: Long, system: TargetSystem, label: String, folderHint: String?): CohortMappingRow

    /**
     * Repoints [cohortId]'s external target at [externalId], keeping the same
     * local `Cohort` row. [subjectId] is the subject the cohort must belong to
     * (the route carries it); a mismatch is rejected so a wrong-path admin call
     * cannot repoint another subject's cohort. Optionally enqueues
     * `cohort.delete-external-target` for the previous target and
     * `cohort.reconcile-list` for the new one.
     */
    fun switchTarget(
        subjectId: Long,
        cohortId: Long,
        externalId: String,
        deletePrevious: Boolean,
        reconcileNow: Boolean,
    ): CohortMappingRow

    /**
     * Resolves [cohortId]'s external target for stale queued
     * `cohort.materialize-target` jobs. Idempotent: returns the existing id
     * when already set; fails terminally when missing. This path never creates
     * provider targets.
     */
    fun materialize(cohortId: Long): CohortTargetRef

    /**
     * Deletes an external target. Driven by
     * [net.blueshell.api.platform.integration.cohort.adapter.job.DeleteExternalTargetJobHandler];
     * provider "already gone" is success.
     */
    fun deleteTarget(system: TargetSystem, externalTargetId: String)
}

/** Result of [CohortTargeting.materialize]: a cohort and its resolved target id. */
data class CohortTargetRef(val cohortId: Long, val externalId: String)
