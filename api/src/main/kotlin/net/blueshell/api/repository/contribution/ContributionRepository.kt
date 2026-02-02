package net.blueshell.api.repository.contribution

import net.blueshell.api.base.BaseRepository
import net.blueshell.api.model.contribution.Contribution
import net.blueshell.api.model.contribution.ContributionPeriod
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ContributionRepository : BaseRepository<Contribution?> {
    @Modifying
    @Query("DELETE FROM Contribution c WHERE c.contributionPeriod = :contributionPeriod")
    fun deleteByContributionPeriod(@Param("contributionPeriod") contributionPeriod: ContributionPeriod?)

    fun findByContributionPeriod(contributionPeriod: ContributionPeriod?): MutableList<Contribution?>?
}
