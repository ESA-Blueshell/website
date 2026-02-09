package net.blueshell.api.feature.contribution.repository

import net.blueshell.api.feature.contribution.model.ContributionReminder
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface ContributionReminderRepository : BaseRepository<ContributionReminder, ContributionReminder.Id> {
    fun findById_ContributionPeriodId(contributionPeriodId: Long): MutableList<ContributionReminder>
}
