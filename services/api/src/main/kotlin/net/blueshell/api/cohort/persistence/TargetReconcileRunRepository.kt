package net.blueshell.api.cohort.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface TargetReconcileRunRepository : JpaRepository<TargetReconcileRun, Long> {
    fun findTop10ByTargetIdOrderByStartedAtDesc(targetId: Long): List<TargetReconcileRun>
}
