package net.blueshell.api.platform.integration.cohort.application

/**
 * Result of one pass of [CohortRuleEvaluator]. The engine computes the
 * desired-vs-current diff for every event that re-evaluates a user; in
 * shadow mode the diff is only logged. The follow-up PR that flips the
 * engine into action will use [toAdd] and [toRemove] to drive the
 * `CohortMember` writes and per-target sync jobs.
 */
data class CohortRuleEvaluation(
    val userId: Long,
    val facts: Set<UserFact>,
    val desired: Set<Long>,
    val current: Set<Long>,
) {
    val toAdd: Set<Long> get() = desired - current
    val toRemove: Set<Long> get() = current - desired
    val isNoOp: Boolean get() = toAdd.isEmpty() && toRemove.isEmpty()

    companion object {
        fun empty(userId: Long): CohortRuleEvaluation =
            CohortRuleEvaluation(userId, emptySet(), emptySet(), emptySet())
    }
}
