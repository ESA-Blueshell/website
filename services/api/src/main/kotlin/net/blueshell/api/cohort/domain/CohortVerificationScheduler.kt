package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Nightly verification sweep. Membership convergence is event-driven and
 * health is established at push time, so this only *verifies*: it enqueues
 * one `ReconcileList` per externally-mapped cohort to detect strangers and
 * members that vanished out of band. The job's dedup key collapses overlap
 * with on-demand reconciles.
 */
@Component
class CohortVerificationScheduler(
    private val cohorts: CohortRepository,
    private val targetIds: CohortTargetIds,
    private val jobs: TrackedJobDispatcher,
) {
    @Scheduled(cron = "\${cohort.verify-cron:0 0 3 * * *}")
    fun verifyAllCohorts() {
        val mapped = cohorts.findAll().filter { cohort ->
            targetIds.find(cohort) != null
        }
        log.info("Scheduling reconcile for {} externally-mapped cohorts", mapped.size)
        mapped.forEach { jobs.runAsync(CohortJobs.ReconcileList, CohortJobs.ReconcileListPayload(it.id!!)) }
    }

    companion object {
        private val log = LoggerFactory.getLogger(CohortVerificationScheduler::class.java)
    }
}
