package net.blueshell.api.cohort.domain

import net.blueshell.api.contribution.api.ContributionPeriodService
import net.blueshell.api.contribution.api.ContributionService
import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.user.application.MembershipService
import net.blueshell.api.cohort.persistence.CohortSubjectType
import org.springframework.stereotype.Component

/** Where a period's cohorts are filed on the external system. */
const val PERIOD_FOLDER = "Periods"

private fun ContributionPeriod.years(): String = "${startDate.year} - ${endDate.year}"

/**
 * Everybody who held a membership during the period.
 *
 * Any membership counts, of any kind, and a membership with no end date is still running — so
 * it belongs to a period that has not started yet as much as to the one under way. This is
 * deliberately the rule the user manager's "member in period" column draws, and the two are
 * implemented separately on purpose: exposing it as a flag would put one boolean per member
 * per period on the wire. Change one and change the other, in
 * `services/frontend/src/composables/useUserRows.ts`.
 */
class PeriodMembersDefinition(
    private val period: ContributionPeriod,
    private val memberships: MembershipService,
) : CohortDefinition {
    override val key = "${CohortSubjectType.PERIOD_MEMBERS}:${period.id}"
    override val type = CohortSubjectType.PERIOD_MEMBERS
    override val scope = period.id
    override val label = "Members ${period.years()}"
    override val folder = PERIOD_FOLDER

    override fun members(): Set<Long> =
        memberships.findUserIdsOverlapping(period.startDate, period.endDate)

    override fun contains(userId: Long): Boolean =
        memberships.heldMembershipBetween(userId, period.startDate, period.endDate)
}

@Component
class PeriodMembersProvider(
    private val periods: ContributionPeriodService,
    private val memberships: MembershipService,
) : CohortDefinitionProvider {
    override val type = CohortSubjectType.PERIOD_MEMBERS

    override fun definitions(): List<CohortDefinition> =
        periods.findAll().filter { it.id != null }.map { PeriodMembersDefinition(it, memberships) }
}

/**
 * Everybody who paid the contribution for the period.
 *
 * A contribution row is the payment: there is nothing else to check, and no amount to compare.
 */
class PeriodPayersDefinition(
    private val period: ContributionPeriod,
    private val contributions: ContributionService,
) : CohortDefinition {
    override val key = "${CohortSubjectType.PERIOD_PAYERS}:${period.id}"
    override val type = CohortSubjectType.PERIOD_PAYERS
    override val scope = period.id
    override val label = "Contribution Paid ${period.years()}"
    override val folder = PERIOD_FOLDER

    override fun members(): Set<Long> =
        contributions.findByContributionPeriodId(period.id!!).map { it.userId }.toSet()

    override fun contains(userId: Long): Boolean =
        contributions.existsByUserIdAndPeriodId(userId, period.id!!)
}

@Component
class PeriodPayersProvider(
    private val periods: ContributionPeriodService,
    private val contributions: ContributionService,
) : CohortDefinitionProvider {
    override val type = CohortSubjectType.PERIOD_PAYERS

    override fun definitions(): List<CohortDefinition> =
        periods.findAll().filter { it.id != null }.map { PeriodPayersDefinition(it, contributions) }
}
