package net.blueshell.api.repository.contribution

import net.blueshell.api.model.contribution.Contribution
import net.blueshell.api.repository.base.BaseRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ContributionRepository : BaseRepository<Contribution, Contribution.Id> {
    @Modifying
    @Query("DELETE FROM Contribution c WHERE c.id.contributionPeriodId = :contributionPeriodId")
    fun deleteByContributionPeriodId(@Param("contributionPeriodId") contributionPeriodId: Long)

    fun findById_ContributionPeriodId(contributionPeriodId: Long): MutableList<Contribution>
}
