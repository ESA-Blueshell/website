package net.blueshell.api.cohort.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface TargetReconcileRunRepository : JpaRepository<TargetReconcileRun, Long> {
    fun findTop10ByCohortIdOrderByStartedAtDesc(cohortId: Long): List<TargetReconcileRun>
}
