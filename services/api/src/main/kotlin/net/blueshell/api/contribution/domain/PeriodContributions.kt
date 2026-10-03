package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.api.ContributionPeriodService
import net.blueshell.api.contribution.persistence.ContributionReminderRepository
import net.blueshell.api.contribution.persistence.ContributionRepository
import net.blueshell.api.contribution.persistence.IncassoNotificationRepository
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.user.api.MembershipService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

/** One member of a period, with what they owe and whether and how they were asked. */
data class PeriodMember(
    val userId: Long,
    val name: String,
    val username: String,
    /** Null for an honorary member, who owes nothing. */
    val feeType: BulkFeeType?,
    val fee: Double?,
    val incasso: Boolean,
    val paid: Boolean,
    val paidAt: Instant?,
    val lastEmailAt: Instant?,
    val lastEmailKind: ContributionEmailKind?,
)

/** One send of payment emails for a period: the emails of one kind that went out in the same minute. */
data class PaymentEmailRun(
    val kind: ContributionEmailKind,
    val sentAt: Instant,
    val recipients: Int,
)

/** A period's money: its members and their payments, and the payment email runs sent for it. */
data class PeriodContributionsView(
    val periodId: Long,
    val members: List<PeriodMember>,
    val runs: List<PaymentEmailRun>,
    /** Newest first; a run with no submittedAt waits for upload to ING. */
    val incassoRuns: List<IncassoRunSummary>,
)

@Service
class PeriodContributions(
    private val periods: ContributionPeriodService,
    private val memberships: MembershipService,
    private val contributions: ContributionRepository,
    private val reminders: ContributionReminderRepository,
    private val notifications: IncassoNotificationRepository,
    private val incassoRuns: IncassoRuns,
) {
    @Transactional(readOnly = true)
    fun of(periodId: Long): PeriodContributionsView {
        val period = periods.findById(periodId)
        val paid = contributions.findByIdContributionPeriodId(periodId).associate { it.userId to it.createdAt }
        val emails =
            reminders.findByContributionPeriod_Id(periodId).map { Triple(it.userId, it.askedAt, ContributionEmailKind.REMINDER) } +
                notifications.findByContributionPeriod_Id(periodId).map {
                    Triple(it.userId, it.askedAt, ContributionEmailKind.INCASSO_NOTIFICATION)
                }
        val lastEmail = emails.groupBy { it.first }.mapValues { (_, sent) -> sent.maxBy { it.second } }
        val members =
            memberships
                .findOverlappingWithMembers(period.startDate, period.endDate)
                .groupBy { it.userId }
                .map { (userId, held) ->
                    val latest = held.maxBy { it.startDate }
                    val feeType = resolveFeeType(latest.memberType, latest.startDate, period)
                    PeriodMember(
                        userId = userId,
                        name = latest.user.fullName,
                        username = latest.user.username,
                        feeType = feeType,
                        fee = feeType?.let { resolveFeeAmount(it, period) },
                        incasso = latest.incasso,
                        paid = userId in paid,
                        paidAt = paid[userId],
                        lastEmailAt = lastEmail[userId]?.second,
                        lastEmailKind = lastEmail[userId]?.third,
                    )
                }.sortedBy { it.name }
        val runs =
            emails
                .groupBy { it.third to it.second.truncatedTo(ChronoUnit.MINUTES) }
                .map { (key, sent) -> PaymentEmailRun(key.first, key.second, sent.size) }
                .sortedByDescending { it.sentAt }
        return PeriodContributionsView(periodId, members, runs, incassoRuns.summariesOf(periodId))
    }
}
