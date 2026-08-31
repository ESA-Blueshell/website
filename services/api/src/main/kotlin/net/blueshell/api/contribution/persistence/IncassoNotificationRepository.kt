package net.blueshell.api.contribution.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface IncassoNotificationRepository : BaseRepository<IncassoNotification, IncassoNotification.Id> {
    fun findByIdContributionPeriodId(contributionPeriodId: Long): MutableList<IncassoNotification>
}
