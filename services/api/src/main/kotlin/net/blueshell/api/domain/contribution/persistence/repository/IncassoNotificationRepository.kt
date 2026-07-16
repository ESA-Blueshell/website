package net.blueshell.api.domain.contribution.persistence.repository

import net.blueshell.api.domain.contribution.persistence.IncassoNotification
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface IncassoNotificationRepository : BaseRepository<IncassoNotification, IncassoNotification.Id> {
    fun findByIdContributionPeriodId(contributionPeriodId: Long): MutableList<IncassoNotification>
}
