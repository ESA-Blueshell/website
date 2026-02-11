package net.blueshell.api.domain.contribution.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface ContributionReminderRepository : BaseRepository<ContributionReminder, ContributionReminder.Id> {
    fun findByIdContributionPeriodId(contributionPeriodId: Long): MutableList<ContributionReminder>
}
