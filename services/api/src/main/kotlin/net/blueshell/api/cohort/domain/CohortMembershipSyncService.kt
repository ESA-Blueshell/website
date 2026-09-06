package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.Cohort
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.sync.api.ExternalIdMappingService
import net.blueshell.api.sync.api.ExternalIdMappingService.Companion.USER_AGGREGATE
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.NonRetryableJobException
import net.blueshell.api.shared.job.JobQueue
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime

/**
 * Drives one `(user, cohort)` sync end to end: resolves both external ids, picks the
 * [TargetStrategy] for the cohort's system and asks it to apply the change.
 *
 * An ADD with no user external id enqueues `SyncContact` and throws retryably, so the retry
 * lands once the contact exists. An ADD with no cohort target id fails terminally — linking a
 * target is an operator's act. A REMOVE with no external state either side is a no-op.
 */
@Service
class CohortMembershipSyncService(
    private val cohorts: CohortRepository,
    private val ledger: CohortLedger,
    private val strategies: TargetStrategies,
    private val externalIds: ExternalIdMappingService,
    private val targetIds: CohortTargetIds,
    private val jobs: JobQueue,
    transactionManager: PlatformTransactionManager,
) : CohortMembershipSync {

    // Suspends the surrounding transaction (this service's own and the
    // @Transactional opened by AbstractJsonJobHandler) so the provider HTTP
    // call holds no DB connection and runs with no transaction active —
    // ADR-006/ADR-023. DB reads/writes stay in the suspended-and-resumed
    // outer transaction around it.
    private val outsideTransaction = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_NOT_SUPPORTED
    }

    @Transactional
    override fun sync(userId: Long, cohortId: Long, intent: SyncCohortMembershipIntent) {
        val cohort = cohorts.findById(cohortId).orElseThrow {
            NonRetryableJobException("Cohort $cohortId not found")
        }
        val system = runCatching { TargetSystem.valueOf(cohort.system) }.getOrElse {
            throw NonRetryableJobException("Cohort $cohortId has unknown system '${cohort.system}'")
        }
        val strategy = strategies.requireForJob(system)

        when (intent) {
            SyncCohortMembershipIntent.ADD -> add(userId, cohort, strategy)
            SyncCohortMembershipIntent.REMOVE -> remove(userId, cohort, strategy)
        }
    }

    private fun add(userId: Long, cohort: Cohort, strategy: TargetStrategy) {
        val cohortId = cohort.id!!
        val system = cohort.system
        val externalUserId = externalIds.find(USER_AGGREGATE, userId, system)?.externalId
        if (externalUserId == null) {
            jobs.runAsync(ContactJobs.SyncContact, ContactJobs.SyncContactPayload(userId))
            throw CohortMembershipNotReadyException(
                "user $userId has no $system external id — enqueued SyncContact, will retry",
            )
        }
        val externalCohortId = targetIds.find(cohort)
        if (externalCohortId == null) {
            throw CohortTargetNotLinkedException(cohortId, system)
        }
        outsideTransaction.executeWithoutResult { strategy.add(strategy.handle(externalCohortId), externalUserId) }

        // Stamp the ledger so the desired row reads as synced. This is the
        // primary path to healthy; reconcile only verifies afterwards.
        if (!ledger.markPushed(cohortId, userId, externalUserId, LocalDateTime.now())) {
            log.warn("Pushed user {} to {} cohort {} but its desired row is gone — not stamping", userId, system, cohortId)
        }
        log.debug("Added user {} to {} cohort {} (ext={})", userId, system, cohortId, externalCohortId)
    }

    private fun remove(userId: Long, cohort: Cohort, strategy: TargetStrategy) {
        val cohortId = cohort.id!!
        val system = cohort.system
        val externalUserId = externalIds.find(USER_AGGREGATE, userId, system)?.externalId
        val externalCohortId = targetIds.find(cohort)
        if (externalUserId == null || externalCohortId == null) {
            log.debug(
                "No $system external ids for user {} / cohort {} — skipping removal",
                userId, cohortId,
            )
            return
        }
        outsideTransaction.executeWithoutResult { strategy.remove(strategy.handle(externalCohortId), externalUserId) }
        log.debug("Removed user {} from {} cohort {} (ext={})", userId, system, cohortId, externalCohortId)
    }

    companion object {
        private val log = LoggerFactory.getLogger(CohortMembershipSyncService::class.java)
    }
}

/**
 * Thrown when the (user, cohort) pair cannot be pushed yet because a
 * prerequisite (typically the user's external contact id) has not been
 * materialised. The exception is *not* annotated as non-retryable, so
 * the job framework re-runs the driving adapter after backoff once
 * the prerequisite job has had a chance to complete.
 */
class CohortMembershipNotReadyException(message: String) : RuntimeException(message)

class CohortTargetNotLinkedException(cohortId: Long, system: String) : NonRetryableJobException(
    "cohort $cohortId has no $system target — create or link an external target, then retry the membership job",
)
