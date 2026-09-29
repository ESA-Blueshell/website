package net.blueshell.api.cohort.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface DriftResolutionRepository : JpaRepository<DriftResolution, Long> {
    fun findTop20ByTargetIdInOrderByResolvedAtDesc(targetIds: Collection<Long>): List<DriftResolution>
}
