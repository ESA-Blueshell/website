package net.blueshell.api.domain.contribution.persistence.repository

import net.blueshell.api.domain.contribution.persistence.ContributionReminder
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface ContributionReminderRepository : BaseRepository<ContributionReminder, ContributionReminder.Id> {
    fun findByIdContributionPeriodId(contributionPeriodId: Long): MutableList<ContributionReminder>
}
