package net.blueshell.api.contribution.persistence

import net.blueshell.api.contribution.domain.model.ContributionPeriod
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface ContributionPeriodRepository : BaseRepository<ContributionPeriod, Long> {
    @Query(
        "SELECT cp FROM ContributionPeriod cp " +
                "WHERE cp.startDate <= CURRENT_DATE " +
                "ORDER BY CASE WHEN CURRENT_DATE BETWEEN cp.startDate AND cp.endDate THEN 0 ELSE 1 END, cp.startDate DESC " +
                "LIMIT 1"
    )
    fun findCurrentOrLatestContributionPeriod(): ContributionPeriod
}
