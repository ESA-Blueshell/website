package net.blueshell.api.repository;

import net.blueshell.api.base.BaseRepository;
import net.blueshell.api.model.ContributionPeriod;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContributionPeriodRepository extends BaseRepository<ContributionPeriod, Long> {

    @Query("SELECT cp FROM ContributionPeriod cp WHERE CURRENT_DATE BETWEEN cp.startDate AND cp.endDate")
    List<ContributionPeriod> findCurrentContributionPeriod();

    @Query("SELECT cp FROM ContributionPeriod cp ORDER BY cp.startDate DESC")
    List<ContributionPeriod> findLatestContributionPeriod();

    @Query("SELECT cp FROM ContributionPeriod cp " +
            "WHERE cp.startDate <= CURRENT_DATE " +
            "ORDER BY CASE WHEN CURRENT_DATE BETWEEN cp.startDate AND cp.endDate THEN 0 ELSE 1 END, cp.startDate DESC")
    List<ContributionPeriod> findCurrentOrLatestContributionPeriod();
}
