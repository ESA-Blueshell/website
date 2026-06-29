package net.blueshell.api.platform.integration.cohort.port.`in`

/**
 * Inbound (driving) port: pushes one `(user, cohort)` membership to
 * its external system. Driving adapters (job handlers, future
 * controllers, schedulers) call this; the implementation in
 * `cohort/application/` owns the business logic.
 *
 * Use case shape: a single deterministic `sync(...)` call. The
 * implementation decides whether to enqueue a prerequisite user sync
 * and retry, whether a missing cohort target is terminal, and how
 * legacy `REMOVE` intents are guarded before provider access. Callers do
 * not branch on these — they hand off the (userId, cohortId, intent)
 * triple and let the application layer route it.
 */
interface CohortMembershipSync {
    fun sync(userId: Long, cohortId: Long, intent: SyncCohortMembershipIntent)
}

/**
 * Direction of a single cohort-membership sync call. `REMOVE` remains for
 * compatibility with old queued payloads; automatic Brevo removal is
 * blocked by the application removal policy.
 */
enum class SyncCohortMembershipIntent { ADD, REMOVE }
