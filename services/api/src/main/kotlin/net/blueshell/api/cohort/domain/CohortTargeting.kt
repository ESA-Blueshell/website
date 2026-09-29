package net.blueshell.api.cohort.domain

import net.blueshell.api.shared.enums.TargetSystem

/**
 * Admin management of a subject's external targets: linking an existing one, creating one, and
 * repointing a mapping at another. An interface because `CohortSubjectController` is written
 * against it — the module publishes this surface to its own web layer.
 *
 * External writes go through [TargetStrategy]. A removal after a switch is handed to the
 * `cohort.delete-external-target` job rather than run inline.
 */
interface CohortTargeting {
    /**
     * Maps the subject's [system] cohort to an existing external target by
     * id. Fails with 409 when the subject already has an active mapping for
     * [system] with a target id; an existing unbound row is filled in place.
     * No external call — the id is trusted.
     */
    fun linkExisting(
        subjectId: Long,
        system: TargetSystem,
        externalId: String,
    ): CohortMappingRow

    /**
     * Creates a new external target on [system] (outside any DB
     * transaction) and maps the subject's [system] cohort to it. Fails with
     * 409 when the subject already has an active mapping for [system].
     */
    fun create(
        subjectId: Long,
        system: TargetSystem,
        label: String,
        folderHint: String?,
    ): CohortMappingRow

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
     * Creates [cohortId]'s external target in its folder and links it (api ADR-035). Idempotent:
     * returns the id when one is set. The cohort is claimed before the provider is called, and a
     * run that finds an earlier claim looks the target up by name before making one, so a retry
     * never makes a second.
     */
    fun createFor(cohortId: Long): CohortTargetRef

    /** Queues a create-target job for every cohort without a target, and says how many. */
    fun createMissing(): Int

    /**
     * Deletes an external target. Run by the `cohort.delete-external-target` job; provider
     * "already gone" is success.
     */
    fun deleteTarget(
        system: TargetSystem,
        externalTargetId: String,
    )
}

/** Result of [CohortTargeting.createFor]: a cohort and its target id. */
data class CohortTargetRef(
    val cohortId: Long,
    val externalId: String,
)
