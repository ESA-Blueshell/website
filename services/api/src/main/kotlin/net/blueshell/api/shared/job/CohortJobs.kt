package net.blueshell.api.shared.job

import net.blueshell.api.platform.integration.cohort.port.`in`.SyncCohortMembershipIntent

/**
 * Per-target cohort membership sync jobs. One job execution pushes one
 * `(user, cohort)` pair to one external system, idempotently. The
 * payload's [SyncCohortMembershipIntent] decides whether the inbound
 * port is called with `ADD` or `REMOVE` semantics — the enum lives on
 * the application port so callers and the driving job handler share
 * one source of truth for the verb.
 *
 * Per-pair fan-out (rather than per-user batch) means a single sync
 * failure is isolated to its own JobExecution row in the Job Manager
 * with its own retry budget.
 */
object CohortJobs {

    object SyncCohortMembership : JobDefinition<SyncCohortMembershipPayload> {
        override val type: String = "cohort.membership-sync"
        override val payloadType: Class<SyncCohortMembershipPayload> =
            SyncCohortMembershipPayload::class.java
    }

    /**
     * Walks every active contribution period and ensures its cohort +
     * `(CONTRIBUTION_PAID, <periodId>)` rule exist. Idempotent.
     */
    object ReconcileAllContributionPeriodCohorts : JobDefinition<ReconcileAllContributionPeriodCohortsPayload> {
        override val type: String = "cohort.reconcile-contribution-periods"
        override val payloadType: Class<ReconcileAllContributionPeriodCohortsPayload> =
            ReconcileAllContributionPeriodCohortsPayload::class.java
        // No dedup: cheap, idempotent, useful to fire on demand multiple times.
        override fun dedupKey(payload: ReconcileAllContributionPeriodCohortsPayload): String? = null
    }

    /**
     * Spawn job: enqueues one [EvaluateUserCohorts] per user. Used to
     * force a full re-evaluation pass — typically after a rule change
     * or to recover from drift.
     */
    object ReconcileAllUserCohorts : JobDefinition<ReconcileAllUserCohortsPayload> {
        override val type: String = "cohort.reconcile-all-users"
        override val payloadType: Class<ReconcileAllUserCohortsPayload> =
            ReconcileAllUserCohortsPayload::class.java
        override fun dedupKey(payload: ReconcileAllUserCohortsPayload): String? = null
    }

    /**
     * Re-evaluates one user's cohort membership against the current
     * rules. Local writes happen here; the per-target push fans out
     * through [SyncCohortMembership] jobs.
     */
    object EvaluateUserCohorts : JobDefinition<EvaluateUserCohortsPayload> {
        override val type: String = "cohort.evaluate-user"
        override val payloadType: Class<EvaluateUserCohortsPayload> =
            EvaluateUserCohortsPayload::class.java
    }

    /**
     * Pushes every active `cohort_member` row for one cohort back to
     * its external system. Does not change local state.
     */
    object ResyncCohort : JobDefinition<ResyncCohortPayload> {
        override val type: String = "cohort.resync"
        override val payloadType: Class<ResyncCohortPayload> =
            ResyncCohortPayload::class.java
    }

    data class SyncCohortMembershipPayload(
        val userId: Long,
        val cohortId: Long,
        val intent: SyncCohortMembershipIntent,
    )

    data class ReconcileAllContributionPeriodCohortsPayload(val unused: Unit = Unit)
    data class ReconcileAllUserCohortsPayload(val unused: Unit = Unit)
    data class EvaluateUserCohortsPayload(val userId: Long)
    data class ResyncCohortPayload(val cohortId: Long)
}
