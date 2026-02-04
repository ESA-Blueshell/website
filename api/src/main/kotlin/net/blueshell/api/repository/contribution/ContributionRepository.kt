package net.blueshell.api.repository.contribution

import net.blueshell.api.base.BaseRepository
import net.blueshell.api.model.contribution.Contribution
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ContributionRepository : BaseRepository<Contribution, Long> {
    @Modifying
    @Query("DELETE FROM Contribution c WHERE c.contributionPeriodId = :contributionPeriodId")
    fun deleteByContributionPeriodId(@Param("contributionPeriodId") contributionPeriodId: Long)

    fun findByContributionPeriodId(contributionPeriodId: Long): MutableList<Contribution>
}
