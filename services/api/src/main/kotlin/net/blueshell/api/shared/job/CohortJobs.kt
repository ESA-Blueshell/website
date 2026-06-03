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
     * rules. Local desired-row writes happen here; touched cohorts
     * converge through [ReconcileList] jobs.
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

    /**
     * Removes one external member from a cohort's external target.
     * Used by the drift-remediation UI to clean up extras.
     * Dedup-key default (payload hash) collapses double-clicks.
     */
    object RemoveExternalMember : JobDefinition<RemoveExternalMemberPayload> {
        override val type: String = "cohort.remove-external-member"
        override val payloadType: Class<RemoveExternalMemberPayload> =
            RemoveExternalMemberPayload::class.java
    }

    /**
     * Fetches the full external member list for one cohort mapping,
     * updates the membership ledger, and enqueues ADD/contact jobs for
     * missing desired members. One network call per run; extras are
     * recorded for admin remediation rather than removed automatically.
     */
    object ReconcileList : JobDefinition<ReconcileListPayload> {
        override val type: String = "cohort.reconcile-list"
        override val payloadType: Class<ReconcileListPayload> =
            ReconcileListPayload::class.java
        override fun dedupKey(payload: ReconcileListPayload): String = "cohort=${payload.cohortId}"
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
    data class RemoveExternalMemberPayload(val cohortId: Long, val externalUserId: String)
    data class ReconcileListPayload(val cohortId: Long)
}
