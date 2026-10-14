package net.blueshell.api.contribution.persistence

import jakarta.persistence.LockModeType
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ContributionPeriodRepository : BaseRepository<ContributionPeriod, Long> {
    @Query(
        "SELECT cp FROM ContributionPeriod cp " +
            "WHERE cp.startDate <= CURRENT_DATE " +
            "ORDER BY CASE WHEN CURRENT_DATE BETWEEN cp.startDate AND cp.endDate THEN 0 ELSE 1 END, cp.startDate DESC " +
            "LIMIT 1",
    )
    fun findCurrentOrLatestContributionPeriod(): ContributionPeriod?

    /** The period, held until the transaction ends, so two writers that judge it in turn see each other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT cp FROM ContributionPeriod cp WHERE cp.id = :id")
    fun findByIdForUpdate(
        @Param("id") id: Long,
    ): ContributionPeriod?
}
