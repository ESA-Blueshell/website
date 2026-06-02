package net.blueshell.api.platform.integration.cohort.application

import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.platform.integration.cohort.persistence.CohortFactKind
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Computes the set of [UserFact]s currently true for a user, by reading
 * the canonical sources on the [net.blueshell.api.domain.user.persistence.User]
 * entity graph.
 *
 * Single place to extend when a new [CohortFactKind] is added. Lazy
 * associations on `User` are walked inside the read-only transaction,
 * so callers can pass just the user id.
 */
@Service
class UserFactCollector(
    private val users: UserService,
    private val periods: ContributionPeriodService,
) {
    @Transactional(readOnly = true)
    fun collect(userId: Long): Set<UserFact> {
        val user = runCatching { users.findById(userId) }.getOrNull() ?: return emptySet()
        val facts = mutableSetOf<UserFact>()

        // ROLE: User.roles is the source of truth (membership / committee /
        // board listeners grant the matching Role values).
        user.roles.forEach { role ->
            facts.add(UserFact(CohortFactKind.ROLE, role.name))
        }

        // COMMITTEE: one fact per committee the user is a member of.
        user.committeeMembers.forEach { membership ->
            val committeeId = membership.committee.id ?: return@forEach
            facts.add(UserFact(CohortFactKind.COMMITTEE, committeeId.toString()))
        }

        // CONTRIBUTION_PAID: one fact per contribution-period the user has
        // an active contribution row for (soft-deleted rows are filtered
        // by @SQLRestriction).
        user.contributions.forEach { contribution ->
            val periodId = contribution.id.contributionPeriodId ?: return@forEach
            facts.add(UserFact(CohortFactKind.CONTRIBUTION_PAID, periodId.toString()))
        }

        // MEMBER_IN_PERIOD / ACTIVE_IN_PERIOD: both pivot on the current
        // contribution period — the engine cannot yet reconstruct
        // historical role / committee state, so past periods are not
        // emitted. The fact for the current period is:
        //   - MEMBER_IN_PERIOD when the user currently holds Role.MEMBER
        //     (matches the "Members <YYYY - YYYY>" cohort label).
        //   - ACTIVE_IN_PERIOD when the user holds any committee
        //     membership today (esports-team activity slots in once
        //     teams gain a persistence layer).
        val currentPeriodId = periods.findLatest()?.id
        if (currentPeriodId != null) {
            if (user.roles.any { it == net.blueshell.api.shared.enums.Role.MEMBER }) {
                facts.add(UserFact(CohortFactKind.MEMBER_IN_PERIOD, currentPeriodId.toString()))
            }
            if (user.committeeMembers.isNotEmpty()) {
                facts.add(UserFact(CohortFactKind.ACTIVE_IN_PERIOD, currentPeriodId.toString()))
            }
        }

        // NEWSLETTER: a single boolean fact; opt-out is represented by
        // the *absence* of a rule, not a "false" fact.
        if (user.newsletter) {
            facts.add(UserFact(CohortFactKind.NEWSLETTER, "true"))
        }

        return facts
    }
}
