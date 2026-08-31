package net.blueshell.api.contribution.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
@Suppress("FunctionName")
interface ContributionReminderRepository : BaseRepository<ContributionReminder, Long> {
    /** Every ask made for this period, in no particular order. A member may appear more than once. */
    fun findByContributionPeriod_Id(contributionPeriodId: Long): MutableList<ContributionReminder>
}
