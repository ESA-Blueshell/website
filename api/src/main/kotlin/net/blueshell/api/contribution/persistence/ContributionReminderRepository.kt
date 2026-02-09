package net.blueshell.api.contribution.persistence

import net.blueshell.api.contribution.persistence.ContributionReminder
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface ContributionReminderRepository : BaseRepository<ContributionReminder, ContributionReminder.Id> {
    fun findById_ContributionPeriodId(contributionPeriodId: Long): MutableList<ContributionReminder>
}
