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

/** One period a person was a member in: what they owe, whether they paid and how they were last asked. */
data class MemberPeriodContribution(
    val periodId: Long,
    val startDate: LocalDate,
    val endDate: LocalDate,
    /** Null for an honorary member, who owes nothing. */
    val feeType: BulkFeeType?,
    val fee: Double?,
    val paid: Boolean,
    val paidAt: Instant?,
    val lastEmailAt: Instant?,
    val lastEmailKind: ContributionEmailKind?,
)

@Service
class MemberContributions(
    private val periods: ContributionPeriodService,
    private val memberships: MembershipService,
    private val contributions: ContributionRepository,
    private val reminders: ContributionReminderRepository,
    private val notifications: IncassoNotificationRepository,
) {
    /** Every period the person held a membership in, newest first. */
    @Transactional(readOnly = true)
    fun of(userId: Long): List<MemberPeriodContribution> {
        val held = memberships.findByUserId(userId)
        val asked = reminders.findByUser_Id(userId).map { Triple(it.contributionPeriodId, it.askedAt, ContributionEmailKind.REMINDER) }
        val told =
            notifications.findByUser_Id(userId).map {
                Triple(it.contributionPeriodId, it.askedAt, ContributionEmailKind.INCASSO_NOTIFICATION)
            }
        val emails = asked + told
        return periods
            .findAll()
            .sortedByDescending { it.startDate }
            .mapNotNull { period ->
                val membership =
                    held
                        .filter { it.startDate <= period.endDate && it.endDate?.let { end -> end >= period.startDate } != false }
                        .maxByOrNull { it.startDate } ?: return@mapNotNull null
                val periodId = requireNotNull(period.id)
                val feeType = resolveFeeType(membership.memberType, membership.startDate, period)
                val payment = contributions.findById(Contribution.Id(userId, periodId)).orElse(null)
                val lastEmail = emails.filter { it.first == periodId }.maxByOrNull { it.second }
                MemberPeriodContribution(
                    periodId = periodId,
                    startDate = period.startDate,
                    endDate = period.endDate,
                    feeType = feeType,
                    fee = feeType?.let { resolveFeeAmount(it, period) },
                    paid = payment != null,
                    paidAt = payment?.createdAt,
                    lastEmailAt = lastEmail?.second,
                    lastEmailKind = lastEmail?.third,
                )
            }
    }
}
