package net.blueshell.api.repository.contribution

import net.blueshell.api.model.contribution.ContributionReminder
import net.blueshell.api.repository.base.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface ContributionReminderRepository : BaseRepository<ContributionReminder, ContributionReminder.Id> {
    fun findById_ContributionPeriodId(contributionPeriodId: Long): MutableList<ContributionReminder>
}
