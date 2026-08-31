package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.api.ContributionPeriodService
import net.blueshell.api.contribution.api.ContributionService
import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.shared.dto.bulk.BulkRowDisposition
import net.blueshell.api.shared.dto.bulk.BulkRowReason
import net.blueshell.api.shared.dto.bulk.FeeCycleGroup
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.user.api.MembershipService
import net.blueshell.api.user.api.UserErasureService
import net.blueshell.api.user.persistence.Membership
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Works out who a period's fee cycle is about, and what each of them owes.
 *
 * The preview and the send both read this, so they cannot disagree about who is included
 * or what they owe. Nothing here writes, enqueues or sends.
 */
@Service
class FeeCyclePlanner(
    private val periods: ContributionPeriodService,
    private val contributions: ContributionService,
    private val memberships: MembershipService,
    private val reminders: ContributionReminderService,
    private val preNotifications: IncassoNotificationService,
    private val erasure: UserErasureService,
) {
    @Transactional(readOnly = true)
    fun plan(contributionPeriodId: Long): FeeCyclePlan {
        val period = periods.findById(contributionPeriodId)

        val paid = contributions.findByContributionPeriodId(contributionPeriodId)
            .map { it.userId }
            .toSet()

        // The whole population in one query. A member of the period is anyone whose
        // membership overlapped it, which is the rule the manager's "member in period"
        // column draws.
        val judged = memberships.findOverlappingWithMembers(period.startDate, period.endDate)
            .groupBy { it.userId }
            .mapValues { (_, held) -> judgedMembership(held) }

        val lastAsked = lastAskedDates(contributionPeriodId)

        val participants = judged
            .filterKeys { it !in paid }
            .map { (userId, membership) -> participant(userId, membership, period, lastAsked) }
            .sortedBy { it.name }

        return FeeCyclePlan(contributionPeriodId = contributionPeriodId, participants = participants)
    }

    /**
     * The membership every decision about this member is judged against.
     *
     * Their active one where they have it: the direct-debit flag on a membership that has
     * ended is not how the member pays now, and sending the wrong statement on a stale flag
     * costs them money. Where none of the memberships that put them in the period is still
     * running — which is every member of a period that has closed — the one that started
     * last stands in, because judging a past period by a membership that did not exist
     * during it would be worse.
     */
    private fun judgedMembership(held: List<Membership>): Membership =
        held.filter { it.endDate == null }.maxByOrNull { it.startDate }
            ?: held.maxBy { it.startDate }

    private fun participant(
        userId: Long,
        membership: Membership,
        period: ContributionPeriod,
        lastAsked: Map<FeeCycleGroup, Map<Long, LocalDate>>,
    ): FeeCycleParticipant {
        val member = membership.user
        val feeType = resolveFeeType(membership.memberType, membership.startDate, period)
        val group = if (membership.incasso) FeeCycleGroup.DIRECT_DEBIT else FeeCycleGroup.TRANSFER

        // Every exclusion here is hard: an honorary member owes nothing, a deleted account is
        // nobody to write to, and an address that is not there cannot be written to. None of
        // the three is a judgement the operator can overrule.
        //
        // Deletion is asked of the erasure snapshot rather than read off the address, which it
        // anonymises to a placeholder that would pass an is-it-blank test and bounce. It does
        // not end the memberships either, so without this a deleted account stays a member of
        // the period and the cycle would write to it.
        val (disposition, reason) = when {
            membership.memberType == MemberType.HONORARY ->
                BulkRowDisposition.EXCLUDED to BulkRowReason.HONORARY
            erasure.isDeleted(userId) ->
                BulkRowDisposition.EXCLUDED to BulkRowReason.DELETED
            member.email.isBlank() ->
                BulkRowDisposition.EXCLUDED to BulkRowReason.NO_EMAIL
            else -> BulkRowDisposition.INCLUDED to null
        }

        return FeeCycleParticipant(
            userId = userId,
            name = member.fullName,
            memberType = membership.memberType,
            memberSince = membership.startDate,
            group = group,
            disposition = disposition,
            reason = reason,
            feeType = feeType,
            amount = feeType?.let { resolveFeeAmount(it, period) },
            lastAskedOn = lastAsked[group]?.get(userId),
        )
    }

    /**
     * When each member was last asked, per side of the partition.
     *
     * Read per group rather than pooled: a member moved onto direct debit part way through a
     * period has been asked by transfer and not yet pre-notified, and saying otherwise would
     * hide the send the treasurer is about to make.
     *
     * The latest of a member's asks, because there can be several — the treasurer chases, and
     * each chase is its own row.
     */
    private fun lastAskedDates(contributionPeriodId: Long): Map<FeeCycleGroup, Map<Long, LocalDate>> = mapOf(
        FeeCycleGroup.TRANSFER to latestAskPerMember(
            reminders.findByContributionPeriodId(contributionPeriodId).map { it.userId to it.askedAt },
        ),
        FeeCycleGroup.DIRECT_DEBIT to latestAskPerMember(
            preNotifications.findByContributionPeriodId(contributionPeriodId).map { it.userId to it.askedAt },
        ),
    )

    private fun latestAskPerMember(asks: List<Pair<Long, Instant>>): Map<Long, LocalDate> = asks
        .groupBy { (userId, _) -> userId }
        .mapValues { (_, theirs) -> theirs.maxOf { (_, askedAt) -> askedAt }.atZone(ZoneOffset.UTC).toLocalDate() }
}
