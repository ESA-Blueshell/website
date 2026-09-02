package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.api.ContributionPeriodService
import net.blueshell.api.contribution.api.ContributionService
import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.shared.dto.bulk.BulkRowDisposition
import net.blueshell.api.shared.dto.bulk.BulkRowReason
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.user.api.MembershipService
import net.blueshell.api.user.api.UserErasureService
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.Membership
import net.blueshell.api.user.persistence.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * What the payment emails would do to a selection. Read by both the preview and the send, so
 * they cannot disagree. Nothing here writes, enqueues or sends.
 */
@Service
class ContributionEmailPlanner(
    private val periods: ContributionPeriodService,
    private val contributions: ContributionService,
    private val memberships: MembershipService,
    private val users: UserService,
    private val reminders: ContributionReminderService,
    private val preNotifications: IncassoNotificationService,
    private val erasure: UserErasureService,
) {
    @Transactional(readOnly = true)
    fun plan(contributionPeriodId: Long, userIds: Collection<Long>): ContributionEmailPlan {
        val period = periods.findById(contributionPeriodId)
        val selected = userIds.distinct()

        val held = memberships.findByUserIdsWithMembers(selected)
        val paid = contributions.findByContributionPeriodId(contributionPeriodId).map { it.userId }.toSet()
        val lastReminded = latestPerMember(
            reminders.findByContributionPeriodId(contributionPeriodId).map { it.userId to it.askedAt },
        )
        val lastNotified = latestPerMember(
            preNotifications.findByContributionPeriodId(contributionPeriodId).map { it.userId to it.askedAt },
        )

        // The membership read already fetched the member; only somebody holding none costs a
        // lookup. An id naming nobody is no row, so the plan names it instead of losing it.
        val (known, unknown) = selected.partition { held.containsKey(it) || users.existsById(it) }

        val rows = known
            .map { userId ->
                val theirs = held[userId] ?: emptyList()
                val member = theirs.firstOrNull()?.user ?: users.findById(userId)
                row(userId, member, theirs, period, userId in paid, lastReminded[userId], lastNotified[userId])
            }
            .sortedBy { it.name }

        return ContributionEmailPlan(contributionPeriodId, rows, unknown.sorted())
    }

    private fun row(
        userId: Long,
        member: User,
        held: List<Membership>,
        period: ContributionPeriod,
        alreadyPaid: Boolean,
        lastRemindedOn: LocalDate?,
        lastNotifiedOn: LocalDate?,
    ): ContributionEmailRow {
        val judged = judgedMembership(held)
        val memberType = judged?.memberType ?: MemberType.NONE
        val feeType = resolveFeeType(memberType, judged?.startDate, period)
        val (disposition, reason) = decide(userId, member, held, period, alreadyPaid, feeType == null)

        return ContributionEmailRow(
            userId = userId,
            name = member.fullName,
            memberType = memberType,
            memberSince = judged?.startDate,
            disposition = disposition,
            reason = reason,
            defaultKind = if (judged?.incasso == true) {
                ContributionEmailKind.INCASSO_NOTIFICATION
            } else {
                ContributionEmailKind.REMINDER
            },
            feeType = feeType,
            amount = feeType?.let { resolveFeeAmount(it, period) },
            lastRemindedOn = lastRemindedOn,
            lastNotifiedOn = lastNotifiedOn,
        )
    }

    /** Hard exclusions first — nothing overrules them — then the two warnings. */
    private fun decide(
        userId: Long,
        member: User,
        held: List<Membership>,
        period: ContributionPeriod,
        alreadyPaid: Boolean,
        owesNothing: Boolean,
    ): Pair<BulkRowDisposition, BulkRowReason?> = when {
        owesNothing -> BulkRowDisposition.EXCLUDED to BulkRowReason.HONORARY

        // Asked of the erasure snapshot: deletion anonymises the address to a placeholder that
        // would pass an is-it-blank test, and leaves the memberships running.
        erasure.isDeleted(userId) -> BulkRowDisposition.EXCLUDED to BulkRowReason.DELETED

        member.email.isBlank() -> BulkRowDisposition.EXCLUDED to BulkRowReason.NO_EMAIL

        alreadyPaid -> BulkRowDisposition.WARNING to BulkRowReason.ALREADY_PAID

        held.none { it.overlaps(period) } -> BulkRowDisposition.WARNING to BulkRowReason.NOT_MEMBER_IN_PERIOD

        else -> BulkRowDisposition.INCLUDED to null
    }

    /**
     * Their active membership where they hold one: a flag on a spell that has ended is not
     * how the member pays now. Otherwise the one that started last.
     */
    private fun judgedMembership(held: List<Membership>): Membership? =
        held.filter { it.endDate == null }.maxByOrNull { it.startDate }
            ?: held.maxByOrNull { it.startDate }

    private fun latestPerMember(sends: List<Pair<Long, Instant>>): Map<Long, LocalDate> = sends
        .groupBy { (userId, _) -> userId }
        .mapValues { (_, theirs) -> theirs.maxOf { (_, at) -> at }.atZone(ZoneOffset.UTC).toLocalDate() }
}

/** A membership running during the period. Mirrored by `overlapsContributionPeriod` in the frontend. */
private fun Membership.overlaps(period: ContributionPeriod): Boolean =
    startDate <= period.endDate && (endDate == null || endDate!! >= period.startDate)
