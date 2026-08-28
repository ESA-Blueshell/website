package net.blueshell.api.cohort.domain

import net.blueshell.api.committee.api.CommitteeMemberService
import net.blueshell.api.committee.api.CommitteeService
import net.blueshell.api.committee.persistence.Committee
import net.blueshell.api.contribution.api.ContributionPeriodService
import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.cohort.persistence.CohortSubjectType
import org.springframework.stereotype.Component

/**
 * Everybody who did something for the association during the period: a committee seat, a
 * board seat, or a place on a team's roster. Any one of them is enough.
 */
class PeriodActiveMembersDefinition(
    private val period: ContributionPeriod,
    private val sources: List<PeriodActivitySource>,
) : CohortDefinition {
    override val key = "${CohortSubjectType.PERIOD_ACTIVE_MEMBERS}:${period.id}"
    override val type = CohortSubjectType.PERIOD_ACTIVE_MEMBERS
    override val scope = period.id
    override val label = "Active Members ${period.startDate.year} - ${period.endDate.year}"
    override val folder = PERIOD_FOLDER

    override fun members(): Set<Long> =
        sources.flatMapTo(mutableSetOf()) { it.activeBetween(period.startDate, period.endDate) }

    override fun contains(userId: Long): Boolean =
        sources.any { it.wasActive(userId, period.startDate, period.endDate) }
}

@Component
class PeriodActiveMembersProvider(
    private val periods: ContributionPeriodService,
    private val sources: List<PeriodActivitySource>,
) : CohortDefinitionProvider {
    override val type = CohortSubjectType.PERIOD_ACTIVE_MEMBERS

    override fun definitions(): List<CohortDefinition> =
        periods.findAll().filter { it.id != null }.map { PeriodActiveMembersDefinition(it, sources) }
}

/**
 * The people who sit on one committee.
 *
 * Current seats, not everybody who ever sat: mailing a committee should reach the people
 * running it now. Its history is what the active-members cohort is for.
 */
class CommitteeMembersDefinition(
    private val committee: Committee,
    private val committeeMembers: CommitteeMemberService,
) : CohortDefinition {
    override val key = "${CohortSubjectType.COMMITTEE_MEMBERS}:${committee.id}"
    override val type = CohortSubjectType.COMMITTEE_MEMBERS
    override val scope = committee.id
    override val label = committee.name
    override val folder = COMMITTEE_FOLDER

    override fun members(): Set<Long> = committeeMembers.findUserIdsOnCommittee(committee.id!!)

    override fun contains(userId: Long): Boolean = userId in members()

    companion object {
        const val COMMITTEE_FOLDER = "Committees"
    }
}

/**
 * One definition per committee that exists.
 *
 * A disbanded committee produces none, which is what leaves its cohort orphaned rather than
 * silently syncing an empty list over the top of a mailing list somebody may still want.
 */
@Component
class CommitteeMembersProvider(
    private val committees: CommitteeService,
    private val committeeMembers: CommitteeMemberService,
) : CohortDefinitionProvider {
    override val type = CohortSubjectType.COMMITTEE_MEMBERS

    override fun definitions(): List<CohortDefinition> =
        committees.findAll().filter { it.id != null }.map { CommitteeMembersDefinition(it, committeeMembers) }
}

/**
 * Everybody who opted into the newsletter.
 *
 * Whether or not they ever activated an account: somebody who asked for the newsletter at
 * signup asked for the newsletter.
 */
class NewsletterSubscribersDefinition(
    private val users: UserService,
) : CohortDefinition {
    override val key = CohortSubjectType.NEWSLETTER_SUBSCRIBERS.name
    override val type = CohortSubjectType.NEWSLETTER_SUBSCRIBERS
    override val scope = null
    override val label = "Newsletter Subscribers"
    override val folder = null

    override fun members(): Set<Long> = users.findNewsletterSubscriberIds()

    override fun contains(userId: Long): Boolean =
        runCatching { users.findById(userId).newsletter }.getOrDefault(false)
}

@Component
class NewsletterSubscribersProvider(
    private val users: UserService,
) : CohortDefinitionProvider {
    override val type = CohortSubjectType.NEWSLETTER_SUBSCRIBERS

    override fun definitions(): List<CohortDefinition> = listOf(NewsletterSubscribersDefinition(users))
}
