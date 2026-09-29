package net.blueshell.api.cohort.domain

import net.blueshell.api.shared.enums.TargetSystem

/**
 * Admin management of a cohort's external targets: linking an existing one, creating one, and
 * repointing a mapping at another. An interface because `CohortController` is written
 * against it — the module publishes this surface to its own web layer.
 *
 * External writes go through [TargetStrategy]. A removal after a switch is handed to the
 * `cohort.delete-external-target` job rather than run inline.
 */
interface CohortTargeting {
    /**
     * Maps the cohort's [system] target to an existing external target by
     * id. Fails with 409 when the cohort already has an active mapping for
     * [system] with a target id; an existing unbound row is filled in place.
     * No external call — the id is trusted.
     */
    fun linkExisting(
        cohortId: Long,
        system: TargetSystem,
        externalId: String,
    ): CohortTargetRow

    /**
     * Creates a new external target on [system] (outside any DB
     * transaction) and maps the cohort's [system] target to it. Fails with
     * 409 when the cohort already has an active mapping for [system].
     */
    fun create(
        cohortId: Long,
        system: TargetSystem,
        label: String,
        folderHint: String?,
    ): CohortTargetRow

    /**
     * Repoints [cohortId]'s external target at [externalId], keeping the same
     * local `Target` row. [cohortId] is the cohort the target must belong to
     * (the route carries it); a mismatch is rejected so a wrong-path admin call
     * cannot repoint another cohort's target. Optionally enqueues
     * `cohort.delete-external-target` for the previous target and
     * `cohort.reconcile-list` for the new one.
     */
    fun switchTarget(
        cohortId: Long,
        targetId: Long,
        externalId: String,
        deletePrevious: Boolean,
        reconcileNow: Boolean,
    ): CohortTargetRow

    /**
     * Creates [cohortId]'s external target in its folder and links it (api ADR-035). Idempotent:
     * returns the id when one is set. The cohort is claimed before the provider is called, and a
     * run that finds an earlier claim looks the target up by name before making one, so a retry
     * never makes a second.
     */
    fun createFor(targetId: Long): CohortTargetRef

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
    val targetId: Long,
    val externalId: String,
)
