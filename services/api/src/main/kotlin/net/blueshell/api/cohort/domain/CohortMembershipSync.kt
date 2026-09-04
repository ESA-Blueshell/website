package net.blueshell.api.cohort.domain

/**
 * Inbound port pushing one `(user, cohort)` membership to its external system, called by job
 * handlers and schedulers.
 *
 * One deterministic `sync(...)`: a caller hands over the triple and never branches on what
 * follows. Whether a missing user sync is enqueued and retried, whether a missing cohort target
 * is terminal, and what a REMOVE does against absent external state are the implementation's.
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
