package net.blueshell.api.cohort.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface DriftResolutionRepository : JpaRepository<DriftResolution, Long> {
    fun findTop20ByTargetIdInOrderByResolvedAtDesc(targetIds: Collection<Long>): List<DriftResolution>

    fun existsByTargetIdAndExternalUserIdAndActionAndResolvedAtAfter(
        targetId: Long,
        externalUserId: String,
        action: DriftResolutionAction,
        after: Instant,
    ): Boolean
}
