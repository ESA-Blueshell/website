package net.blueshell.api.platform.integration.cohort.application

import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortMemberRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRuleRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Decides which cohorts a user should belong to, based on the
 * [UserFact]s currently true for them and the enabled [CohortRule]s.
 *
 * Shadow mode (this PR): computes the diff and logs it. Does *not*
 * write `CohortMember` rows or enqueue any per-target sync jobs. The
 * follow-up PR turns the diff into actual side effects.
 *
 * Keeping the read+diff phase in one place behind a stable contract
 * means the cutover PR only swaps the "log" tail for "persist + fan
 * out" without touching listener wiring.
 */
@Service
class CohortRuleEvaluator(
    private val userFactCollector: UserFactCollector,
    private val rules: CohortRuleRepository,
    private val memberships: CohortMemberRepository,
) {
    @Transactional(readOnly = true)
    fun evaluate(userId: Long): CohortRuleEvaluation {
        val facts = userFactCollector.collect(userId)
        val desired = facts.flatMap { fact ->
            rules.findAllByFactKindAndFactKeyAndEnabledTrue(fact.kind, fact.key)
        }.mapNotNull { it.cohort.id }.toSet()
        val current = memberships.findAllByUserId(userId).mapNotNull { it.cohort.id }.toSet()

        val evaluation = CohortRuleEvaluation(userId, facts, desired, current)
        logShadow(evaluation)
        return evaluation
    }

    private fun logShadow(e: CohortRuleEvaluation) {
        if (e.isNoOp) {
            log.debug(
                "[cohort-shadow] user={} facts={} cohorts unchanged (membership={})",
                e.userId, e.facts.size, e.current,
            )
            return
        }
        log.info(
            "[cohort-shadow] user={} facts={} would add={} would remove={} current={} desired={}",
            e.userId, e.facts.size, e.toAdd, e.toRemove, e.current, e.desired,
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(CohortRuleEvaluator::class.java)
    }
}
