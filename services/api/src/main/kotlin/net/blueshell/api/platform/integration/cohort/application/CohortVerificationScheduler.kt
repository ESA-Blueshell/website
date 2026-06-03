package net.blueshell.api.platform.integration.cohort.application

import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.sync.application.ExternalIdMappingService
import net.blueshell.api.platform.integration.sync.application.ExternalIdMappingService.Companion.COHORT_AGGREGATE
import net.blueshell.api.shared.job.CohortJobs
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
    private val externalIds: ExternalIdMappingService,
    private val jobs: TrackedJobDispatcher,
) {
    @Scheduled(cron = "\${cohort.verify-cron:0 0 3 * * *}")
    fun verifyAllCohorts() {
        val mapped = cohorts.findAll().filter { cohort ->
            externalIds.find(COHORT_AGGREGATE, cohort.id!!, cohort.system) != null
        }
        log.info("Scheduling reconcile for {} externally-mapped cohorts", mapped.size)
        mapped.forEach { jobs.enqueue(CohortJobs.ReconcileList, CohortJobs.ReconcileListPayload(it.id!!)) }
    }

    companion object {
        private val log = LoggerFactory.getLogger(CohortVerificationScheduler::class.java)
    }
}
