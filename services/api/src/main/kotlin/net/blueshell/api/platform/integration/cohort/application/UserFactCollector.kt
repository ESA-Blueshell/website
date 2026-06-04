package net.blueshell.api.platform.integration.cohort.application

import net.blueshell.api.domain.committee.application.CommitteeMemberService
import net.blueshell.api.domain.committee.application.CommitteeMembershipWindow
import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.Membership
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.platform.integration.cohort.persistence.CohortFactKind
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Computes the set of [UserFact]s currently true for a user, by reading
 * the canonical sources on the [User] entity graph.
 *
 * The user is loaded once into a [UserFactContext]; each fact kind is a
 * small private unit deriving from that shared context. Period data is
 * loaded lazily and only when a membership or committee window makes it
 * relevant. Single place to extend when a new [CohortFactKind] is added.
 *
 * Lazy associations on `User` are walked inside the read-only transaction,
 * so callers can pass just the user id; the units do not rely on
 * `hibernate.enable_lazy_load_no_trans`.
 */
@Service
class UserFactCollector(
    private val users: UserService,
    private val periods: ContributionPeriodService,
    private val committeeMembers: CommitteeMemberService,
) {
    @Transactional(readOnly = true)
    fun collect(userId: Long): Set<UserFact> = collect(userId, LocalDate.now())

    /**
     * Overload taking an explicit [today], so open-ended overlap is
     * deterministic in tests. Helpers never call `LocalDate.now()` themselves.
     */
    @Transactional(readOnly = true)
    fun collect(userId: Long, today: LocalDate): Set<UserFact> {
        val user = runCatching { users.findById(userId) }.getOrNull() ?: return emptySet()
        val ctx = UserFactContext(
            userId = userId,
            user = user,
            today = today,
            periodsLoader = { periods.findAll() },
            windowsLoader = { committeeMembers.findMembershipWindowsForUser(userId) },
        )
        return buildSet {
            addAll(roleFacts(ctx))
            addAll(committeeFacts(ctx))
            addAll(contributionPaidFacts(ctx))
            addAll(membershipPeriodFacts(ctx))
            addAll(newsletterFacts(ctx))
        }
    }

    /** User.roles is the source of truth — membership/committee/board listeners grant the Role values. */
    private fun roleFacts(ctx: UserFactContext): Set<UserFact> =
        ctx.user.roles.mapTo(mutableSetOf()) { UserFact(CohortFactKind.ROLE, it.name) }

    /** One fact per committee the user is currently a member of. */
    private fun committeeFacts(ctx: UserFactContext): Set<UserFact> =
        ctx.user.committeeMembers.mapNotNullTo(mutableSetOf()) { membership ->
            membership.committee.id?.let { UserFact(CohortFactKind.COMMITTEE, it.toString()) }
        }

    /** One fact per period the user has an active contribution row for (soft-deleted rows filtered by @SQLRestriction). */
    private fun contributionPaidFacts(ctx: UserFactContext): Set<UserFact> =
        ctx.user.contributions.mapNotNullTo(mutableSetOf()) { contribution ->
            contribution.id.contributionPeriodId?.let { UserFact(CohortFactKind.CONTRIBUTION_PAID, it.toString()) }
        }

    /**
     * Per-period overlap facts:
     *   - MEMBER_IN_PERIOD: a [Membership] [startDate, endDate] intersects the period; an open-ended
     *     membership (endDate=null) extends to [UserFactContext.today].
     *   - ACTIVE_IN_PERIOD: a committee membership window (including soft-deleted "left on" rows) intersects.
     *
     * Loads periods only when there is a membership or window candidate, so a user with neither never
     * calls `periods.findAll()`.
     */
    private fun membershipPeriodFacts(ctx: UserFactContext): Set<UserFact> {
        val hasMembership = ctx.user.memberships.isNotEmpty()
        val hasWindow = ctx.committeeWindows.isNotEmpty()
        if (!hasMembership && !hasWindow) return emptySet()

        val facts = mutableSetOf<UserFact>()
        ctx.periods.forEach { period ->
            val periodId = period.id ?: return@forEach
            if (hasMembership && ctx.user.memberships.any { it.overlaps(period, ctx.today) }) {
                facts.add(UserFact(CohortFactKind.MEMBER_IN_PERIOD, periodId.toString()))
            }
            if (hasWindow && ctx.committeeWindows.any { it.overlaps(period) }) {
                facts.add(UserFact(CohortFactKind.ACTIVE_IN_PERIOD, periodId.toString()))
            }
        }
        return facts
    }

    /** A single boolean fact; opt-out is the absence of a rule, not a "false" fact. */
    private fun newsletterFacts(ctx: UserFactContext): Set<UserFact> =
        if (ctx.user.newsletter) setOf(UserFact(CohortFactKind.NEWSLETTER, "true")) else emptySet()

    private fun Membership.overlaps(period: ContributionPeriod, today: LocalDate): Boolean {
        val start = startDate
        val end = endDate ?: today
        return !start.isAfter(period.endDate) && !end.isBefore(period.startDate)
    }

    private fun CommitteeMembershipWindow.overlaps(period: ContributionPeriod): Boolean {
        val periodStart = period.startDate.atStartOfDay().toInstant(ZoneOffset.UTC)
        val periodEnd = period.endDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        // overlap = joinedAt < periodEnd && leftAt > periodStart
        return joinedAt.isBefore(periodEnd) && leftAt.isAfter(periodStart)
    }
}

/**
 * The shared, lazily-loaded read context for one [UserFactCollector.collect] call.
 * The user is loaded once; [periods] and [committeeWindows] load on first access so a
 * fact kind that does not need them never triggers the query.
 */
class UserFactContext(
    val userId: Long,
    val user: User,
    val today: LocalDate,
    periodsLoader: () -> List<ContributionPeriod>,
    windowsLoader: () -> List<CommitteeMembershipWindow>,
) {
    val periods by lazy(periodsLoader)
    val committeeWindows by lazy(windowsLoader)
}
