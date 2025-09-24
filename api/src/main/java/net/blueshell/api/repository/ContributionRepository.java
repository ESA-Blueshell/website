package net.blueshell.api.repository;

import net.blueshell.api.base.BaseRepository;
import net.blueshell.api.model.Contribution;
import net.blueshell.api.model.ContributionPeriod;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContributionRepository extends BaseRepository<Contribution> {

    @Modifying
    @Query("DELETE FROM Contribution c WHERE c.contributionPeriod = :contributionPeriod")
    void deleteByContributionPeriod(@Param("contributionPeriod") ContributionPeriod contributionPeriod);

    List<Contribution> findByContributionPeriod(ContributionPeriod contributionPeriod);

    List<Contribution> findByContributionPeriodAndPaid(ContributionPeriod contributionPeriod, Boolean paid);
}
