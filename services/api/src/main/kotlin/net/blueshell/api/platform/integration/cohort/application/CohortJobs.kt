package net.blueshell.api.platform.integration.cohort.application

import net.blueshell.api.platform.integration.cohort.port.`in`.SyncCohortMembershipIntent
import net.blueshell.api.shared.job.JobDefinition

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
     * Walks every active contribution period and ensures its three period
     * cohorts — contribution-paid, members and active-members — and their
     * subjects exist. Idempotent.
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
     * rules. Local desired-row writes happen here, and the evaluator
     * enqueues one per-member [SyncCohortMembership] ADD/REMOVE job for
     * each cohort the user joins or leaves. [ReconcileList] is the
     * separate periodic verifier, not this job's convergence path.
     */
    object EvaluateUserCohorts : JobDefinition<EvaluateUserCohortsPayload> {
        override val type: String = "cohort.evaluate-user"
        override val payloadType: Class<EvaluateUserCohortsPayload> =
            EvaluateUserCohortsPayload::class.java
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

    /**
     * Deletes one external target (Brevo list / Discord role / Google
     * group). Enqueued by `switchTarget` when "delete previous" is set;
     * the adapter treats provider "already gone" as idempotent success.
     */
    object DeleteExternalTarget : JobDefinition<DeleteExternalTargetPayload> {
        override val type: String = "cohort.delete-external-target"
        override val payloadType: Class<DeleteExternalTargetPayload> =
            DeleteExternalTargetPayload::class.java
    }

    object ApplyInboundReconcile : JobDefinition<ApplyInboundReconcilePayload> {
        override val type: String = "cohort.inbound-reconcile-apply"
        override val payloadType: Class<ApplyInboundReconcilePayload> =
            ApplyInboundReconcilePayload::class.java
    }

    /**
     * Stale compatibility job for cohorts that already have a target id.
     * It returns the existing id or fails terminally when missing; target
     * creation is now explicit operator action only.
     */
    object MaterializeCohortTarget : JobDefinition<MaterializeCohortTargetPayload> {
        override val type: String = "cohort.materialize-target"
        override val payloadType: Class<MaterializeCohortTargetPayload> =
            MaterializeCohortTargetPayload::class.java
        override fun dedupKey(payload: MaterializeCohortTargetPayload): String = "cohort=${payload.cohortId}"
    }

    data class SyncCohortMembershipPayload(
        val userId: Long,
        val cohortId: Long,
        val intent: SyncCohortMembershipIntent,
    )

    data class ReconcileAllContributionPeriodCohortsPayload(val unused: Unit = Unit)
    data class ReconcileAllUserCohortsPayload(val unused: Unit = Unit)
    data class EvaluateUserCohortsPayload(val userId: Long)
    data class RemoveExternalMemberPayload(val cohortId: Long, val externalUserId: String)
    data class ReconcileListPayload(val cohortId: Long)
    /** `system` holds a `TargetSystem.name()`; shared/job cannot depend on the sync.port package. */
    data class DeleteExternalTargetPayload(val system: String, val externalTargetId: String)
    data class MaterializeCohortTargetPayload(val cohortId: Long)
    data class ApplyInboundReconcilePayload(
        val subjectId: Long,
        val cohortId: Long,
        val system: String,
        val externalTargetId: String,
        /** Which cohort this is reconciling into, by the key of the definition behind it. */
        val definitionKey: String,
        val selected: List<InboundReconcileSelectedUser>,
    )
    data class InboundReconcileSelectedUser(val externalUserId: String, val userId: Long)
}
