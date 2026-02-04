package net.blueshell.api.repository.contribution

import net.blueshell.api.base.BaseRepository
import net.blueshell.api.model.contribution.ContributionPeriod
import net.blueshell.api.model.contribution.ContributionReminder
import org.springframework.stereotype.Repository

@Repository
interface ContributionReminderRepository : BaseRepository<ContributionReminder, Long> {
    fun findByContributionPeriod(contributionPeriod: ContributionPeriod): MutableList<ContributionReminder>
}
