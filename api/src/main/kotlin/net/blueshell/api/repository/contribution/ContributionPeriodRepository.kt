package net.blueshell.api.repository.contribution

import net.blueshell.api.base.BaseRepository
import net.blueshell.api.model.contribution.ContributionPeriod
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface ContributionPeriodRepository : BaseRepository<ContributionPeriod> {
    @Query(
        ("SELECT cp FROM ContributionPeriod cp " +
                "WHERE cp.startDate <= CURRENT_DATE " +
                "ORDER BY CASE WHEN CURRENT_DATE BETWEEN cp.startDate AND cp.endDate THEN 0 ELSE 1 END, cp.startDate DESC " +
                "LIMIT 1")
    )
    fun findCurrentOrLatestContributionPeriod(): ContributionPeriod
}
