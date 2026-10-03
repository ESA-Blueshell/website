package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.api.ContributionPeriodService
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.user.api.MembershipService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/** The contribution that makes a pending membership active, for the latest period. */
data class FirstContribution(
    val membershipStartDate: LocalDate,
    val periodId: Long?,
    val periodStartDate: LocalDate?,
    val periodEndDate: LocalDate?,
    /** Null where no period is set up yet, so there is no fee to name. */
    val feeType: BulkFeeType?,
    val amount: Double?,
)

/** What a pending member pays to become a member (api ADR-036). */
@Service
class FirstContributions(
    private val memberships: MembershipService,
    private val periods: ContributionPeriodService,
) {
    /** Null where the user holds no pending membership. */
    @Transactional(readOnly = true)
    fun owedBy(userId: Long): FirstContribution? {
        val pending = memberships.findByUserId(userId).firstOrNull { it.isPending } ?: return null
        val period = periods.findLatest()
        val feeType = period?.let { resolveFeeType(pending.memberType, pending.startDate, it) }
        return FirstContribution(
            membershipStartDate = pending.startDate,
            periodId = period?.id,
            periodStartDate = period?.startDate,
            periodEndDate = period?.endDate,
            feeType = feeType,
            amount = feeType?.let { resolveFeeAmount(it, requireNotNull(period)) },
        )
    }
}
