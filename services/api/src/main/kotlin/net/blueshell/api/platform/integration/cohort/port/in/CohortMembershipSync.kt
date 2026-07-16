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
 * `REMOVE` interacts with missing external state. Callers do not branch
 * on these — they hand off the (userId, cohortId, intent) triple and
 * let the application layer route it.
 */
interface CohortMembershipSync {
    fun sync(userId: Long, cohortId: Long, intent: SyncCohortMembershipIntent)
}

/**
 * Direction of a single cohort-membership sync call. Lives on the
 * inbound port so callers and the implementation share one source
 * of truth for the verb.
 */
enum class SyncCohortMembershipIntent { ADD, REMOVE }
