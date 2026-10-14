package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.api.ContributionPeriodService
import net.blueshell.api.contribution.persistence.Contribution
import net.blueshell.api.contribution.persistence.ContributionReminderRepository
import net.blueshell.api.contribution.persistence.ContributionRepository
import net.blueshell.api.contribution.persistence.IncassoNotificationRepository
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.user.api.MembershipService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate

/** What a member reads about their own contributions. */
data class OwnContributions(
    /** Whether the person holds or held a membership, pending ones included. */
    val held: Boolean,
    /** Whether their latest membership is marked to pay by direct debit. */
    val paysByIncasso: Boolean,
    /** Newest first. */
    val periods: List<MemberPeriodContribution>,
)

/** One period a membership overlaps: the fee, whether it is paid and the latest payment email for it. */
data class MemberPeriodContribution(
    val periodId: Long,
    val startDate: LocalDate,
    val endDate: LocalDate,
    /** Null for an honorary membership, which pays nothing. */
    val feeType: BulkFeeType?,
    val fee: Double?,
    /** When the board recorded the payment, which can be after the money arrived. Null while unpaid. */
    val paidAt: Instant?,
    val lastEmailAt: Instant?,
    val lastEmailKind: ContributionEmailKind?,
    /** The latest email's due date, or the debit date of an incasso notification. */
    val dueDate: LocalDate?,
)

private data class Sent(
    val periodId: Long,
    val at: Instant,
    val kind: ContributionEmailKind,
    val due: LocalDate?,
)

@Service
class MemberContributions(
    private val periods: ContributionPeriodService,
    private val memberships: MembershipService,
    private val contributions: ContributionRepository,
    private val reminders: ContributionReminderRepository,
    private val notifications: IncassoNotificationRepository,
) {
    @Transactional(readOnly = true)
    fun of(userId: Long): OwnContributions {
        val held = memberships.findByUserId(userId)
        val asked =
            reminders.findByUser_Id(userId).map {
                Sent(it.contributionPeriodId, it.askedAt, ContributionEmailKind.REMINDER, it.paymentDueDate)
            }
        val told =
            notifications.findByUser_Id(userId).map {
                Sent(it.contributionPeriodId, it.askedAt, ContributionEmailKind.INCASSO_NOTIFICATION, it.debitDate)
            }
        val sent = asked + told
        val rows =
            periods
                .findAll()
                .sortedByDescending { it.startDate }
                .mapNotNull { period ->
                    val membership =
                        held
                            .filter { it.startDate <= period.endDate && it.endDate?.let { end -> end >= period.startDate } != false }
                            .maxByOrNull { it.startDate } ?: return@mapNotNull null
                    val periodId = requireNotNull(period.id)
                    val feeType = resolveFeeType(membership.memberType, membership.startDate, period)
                    val last = sent.filter { it.periodId == periodId }.maxByOrNull { it.at }
                    MemberPeriodContribution(
                        periodId = periodId,
                        startDate = period.startDate,
                        endDate = period.endDate,
                        feeType = feeType,
                        fee = feeType?.let { resolveFeeAmount(it, period) },
                        paidAt = contributions.findById(Contribution.Id(userId, periodId)).orElse(null)?.createdAt,
                        lastEmailAt = last?.at,
                        lastEmailKind = last?.kind,
                        dueDate = last?.due,
                    )
                }
        return OwnContributions(
            held = held.isNotEmpty(),
            paysByIncasso = held.maxByOrNull { it.startDate }?.incasso == true,
            periods = rows,
        )
    }
}
