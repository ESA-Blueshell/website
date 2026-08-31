package net.blueshell.api.contribution.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
@Suppress("FunctionName")
interface IncassoNotificationRepository : BaseRepository<IncassoNotification, Long> {
    /** Every notification sent for this period. A member may appear more than once. */
    fun findByContributionPeriod_Id(contributionPeriodId: Long): MutableList<IncassoNotification>
}
