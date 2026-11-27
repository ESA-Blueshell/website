package net.blueshell.api.contribution.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
@Suppress("FunctionName")
interface IncassoNotificationRepository : BaseRepository<IncassoNotification, Long> {
    /** Every notification sent for this period. A member may appear more than once. */
    fun findByContributionPeriod_Id(contributionPeriodId: Long): MutableList<IncassoNotification>

    /** Every notification sent to this person, for any period, in no particular order. */
    fun findByUser_Id(userId: Long): List<IncassoNotification>

    /** The notices recorded for this member and debit; whether one arrived is read from its job. */
    fun findByUser_IdAndContributionPeriod_IdAndDebitDate(
        userId: Long,
        contributionPeriodId: Long,
        debitDate: LocalDate,
    ): List<IncassoNotification>
}
