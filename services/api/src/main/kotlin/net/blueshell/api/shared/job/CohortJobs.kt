package net.blueshell.api.shared.job

/**
 * Per-target cohort membership sync jobs. One job execution pushes one
 * `(user, cohort)` pair to one external system, idempotently. The
 * [SyncCohortMembershipPayload.intent] decides whether the adapter is
 * called with [SyncCohortMembershipIntent.ADD] or
 * [SyncCohortMembershipIntent.REMOVE] semantics.
 *
 * Per-pair fan-out (rather than per-user batch) means a single
 * adapter failure is isolated to its own job execution row in the
 * Job Manager with its own retry budget — the same observability
 * model as the existing `ContactJobs.SyncListMembership` it
 * supersedes.
 */
object CohortJobs {

    object SyncCohortMembership : JobDefinition<SyncCohortMembershipPayload> {
        override val type: String = "cohort.membership-sync"
        override val payloadType: Class<SyncCohortMembershipPayload> =
            SyncCohortMembershipPayload::class.java
    }

    data class SyncCohortMembershipPayload(
        val userId: Long,
        val cohortId: Long,
        val intent: SyncCohortMembershipIntent,
    )

    enum class SyncCohortMembershipIntent { ADD, REMOVE }
}
