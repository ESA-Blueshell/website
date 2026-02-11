package net.blueshell.api.domain.contribution.persistence.repository

import net.blueshell.api.domain.contribution.persistence.Contribution
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ContributionRepository : BaseRepository<Contribution, Contribution.Id> {
    @Modifying
    @Query("DELETE FROM Contribution c WHERE c.id.contributionPeriodId = :contributionPeriodId")
    fun deleteByContributionPeriodId(@Param("contributionPeriodId") contributionPeriodId: Long)

    fun findByIdContributionPeriodId(contributionPeriodId: Long): MutableList<Contribution>
}
