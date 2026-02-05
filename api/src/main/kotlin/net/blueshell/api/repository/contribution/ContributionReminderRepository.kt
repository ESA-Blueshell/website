package net.blueshell.api.repository.contribution

import net.blueshell.api.repository.base.BaseRepository
import net.blueshell.api.model.contribution.ContributionReminder
import net.blueshell.api.model.contribution.ContributionReminderId
import org.springframework.stereotype.Repository

@Repository
interface ContributionReminderRepository : BaseRepository<ContributionReminder, ContributionReminderId> {
    fun findById_ContributionPeriodId(contributionPeriodId: Long): MutableList<ContributionReminder>
}
