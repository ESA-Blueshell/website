package net.blueshell.api.contribution.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface IncassoRunRepository : JpaRepository<IncassoRun, Long> {
    fun findByContributionPeriodIdOrderByCreatedAtDesc(contributionPeriodId: Long): List<IncassoRun>
}
