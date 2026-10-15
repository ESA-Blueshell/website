package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
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
    private val targets: TargetRepository,
    private val targetExternalIds: CohortTargetIds,
    private val jobs: JobQueue,
    private val definitions: CohortDefinitionRegistry,
    private val updater: CohortMembershipUpdater,
) {
    @Scheduled(cron = "\${cohort.verify-cron:0 0 3 * * *}")
    fun verifyAllTargets() {
        val mapped =
            targets.findAll().filter { target ->
                targetExternalIds.find(target) != null
            }
        log.info("Scheduling reconcile for {} externally-mapped targets", mapped.size)
        mapped.forEach { jobs.reconcileTarget(it.id!!, JobTrigger.SCHEDULED_RUN) }
    }

    // The activists follow the board in office and a team its line-up in the season fielded now,
    // which change by date and with no event.
    @Scheduled(cron = "\${cohort.recompute-cron:0 30 2 * * *}")
    fun recomputeDatedCohorts() {
        definitions.all().filter { it.type in DATED }.forEach(updater::updateCohort)
    }

    companion object {
        private val DATED = setOf(CohortType.ACTIVISTS, CohortType.TEAM_PLAYERS)

        private val log = LoggerFactory.getLogger(CohortVerificationScheduler::class.java)
    }
}
