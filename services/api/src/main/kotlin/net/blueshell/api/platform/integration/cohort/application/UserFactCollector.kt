package net.blueshell.api.platform.integration.cohort.application

import net.blueshell.api.domain.committee.application.CommitteeMemberService
import net.blueshell.api.domain.committee.application.CommitteeMembershipWindow
import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.Membership
import net.blueshell.api.platform.integration.cohort.persistence.CohortFactKind
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneOffset

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
    private val committeeMembers: CommitteeMemberService,
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

        // COMMITTEE: one fact per committee the user is currently a member of.
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

        // MEMBER_IN_PERIOD / ACTIVE_IN_PERIOD: per-period overlap facts.
        //   - MEMBER_IN_PERIOD: any Membership row whose [startDate, endDate]
        //     intersects the period dates. An open-ended membership
        //     (endDate=null) extends to "today" for overlap purposes.
        //   - ACTIVE_IN_PERIOD: any committee_members row (including
        //     soft-deleted ones, which represent "left the committee on
        //     this date") whose [joinedAt, leftAt] intersects the period.
        //     Esports-team activity will slot into the same fact kind
        //     once teams gain a persistence layer.
        val allPeriods = periods.findAll()
        if (allPeriods.isNotEmpty()) {
            val committeeWindows = committeeMembers.findMembershipWindowsForUser(userId)

            allPeriods.forEach { period ->
                val periodId = period.id ?: return@forEach
                if (user.memberships.any { it.overlaps(period) }) {
                    facts.add(UserFact(CohortFactKind.MEMBER_IN_PERIOD, periodId.toString()))
                }
                if (committeeWindows.any { it.overlaps(period) }) {
                    facts.add(UserFact(CohortFactKind.ACTIVE_IN_PERIOD, periodId.toString()))
                }
            }
        }

        // NEWSLETTER: a single boolean fact; opt-out is represented by
        // the *absence* of a rule, not a "false" fact.
        if (user.newsletter) {
            facts.add(UserFact(CohortFactKind.NEWSLETTER, "true"))
        }

        return facts
    }

    private fun Membership.overlaps(period: ContributionPeriod): Boolean {
        val start = startDate
        val end = endDate ?: LocalDate.now()
        return !start.isAfter(period.endDate) && !end.isBefore(period.startDate)
    }

    private fun CommitteeMembershipWindow.overlaps(period: ContributionPeriod): Boolean {
        val periodStart = period.startDate.atStartOfDay().toInstant(ZoneOffset.UTC)
        val periodEnd = period.endDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        // overlap = joinedAt < periodEnd && leftAt > periodStart
        return joinedAt.isBefore(periodEnd) && leftAt.isAfter(periodStart)
    }

}
