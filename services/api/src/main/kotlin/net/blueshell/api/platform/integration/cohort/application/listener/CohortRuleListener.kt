package net.blueshell.api.platform.integration.cohort.application.listener

import net.blueshell.api.domain.committee.application.event.CommitteeMembershipChanged
import net.blueshell.api.domain.contribution.application.event.ContributionChanged
import net.blueshell.api.domain.user.application.event.MembershipChanged
import net.blueshell.api.domain.user.application.event.UserCreated
import net.blueshell.api.domain.user.application.event.UserDeleted
import net.blueshell.api.domain.user.application.event.UserUpdated
import net.blueshell.api.platform.integration.cohort.application.CohortRuleEvaluator
import net.blueshell.api.platform.integration.cohort.application.CommitteeCohortResolver
import net.blueshell.api.platform.integration.cohort.application.ContributionPeriodCohortResolver
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Funnels every change event that can alter a user's [UserFact]s into a
 * single re-evaluation of cohort membership. The evaluator writes the
 * desired `cohort_member` rows and enqueues per-member
 * `cohort.membership-sync` ADD/REMOVE jobs — it is not shadow mode.
 *
 * The engine still diffs cohort rows; the subject-level engine sketched
 * by V72 is a deferred follow-up (see [CohortSubject]).
 *
 * `REQUIRES_NEW` mirrors the other listeners in the codebase: the
 * re-evaluation runs in its own transaction so an evaluator failure
 * cannot roll back the originating change.
 */
@Component
class CohortRuleListener(
    private val evaluator: CohortRuleEvaluator,
    private val contributionPeriodCohorts: ContributionPeriodCohortResolver,
    private val committeeCohorts: CommitteeCohortResolver,
) {
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onUserCreated(evt: UserCreated) {
        evaluator.evaluate(evt.userId)
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onUserUpdated(evt: UserUpdated) {
        evaluator.evaluate(evt.userId)
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onUserDeleted(evt: UserDeleted) {
        // After deletion the fact collector returns an empty set, so the
        // diff is "remove from every cohort the user was in". Exactly the
        // semantics we want.
        evaluator.evaluate(evt.userId)
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onMembershipChanged(evt: MembershipChanged) {
        evaluator.evaluate(evt.userId)
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onCommitteeMembershipChanged(evt: CommitteeMembershipChanged) {
        // New committees (created post-V69) have no cohort/rule yet —
        // materialise them so the evaluator finds the rule for the
        // `(COMMITTEE, <committeeId>)` fact. Idempotent: no-op when
        // the rule already exists (steady state after first member).
        committeeCohorts.materialize(evt.committeeId)
        evaluator.evaluate(evt.userId)
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onContributionChanged(evt: ContributionChanged) {
        // Brand-new periods (created post-cutover) have no cohort/rule yet —
        // materialise them so the evaluator finds the rule for the
        // `(CONTRIBUTION_PAID, <periodId>)` fact. Idempotent: no-op when
        // the rule already exists (which is the steady state once a
        // period's first contribution has been seen).
        contributionPeriodCohorts.materialize(evt.periodId)
        evaluator.evaluate(evt.userId)
    }
}
