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

    data class SyncCohortMembershipPayload(
        val userId: Long,
        val cohortId: Long,
        val intent: SyncCohortMembershipIntent,
    )
}
