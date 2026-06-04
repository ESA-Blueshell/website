package net.blueshell.api.platform.integration.cohort.application

import net.blueshell.api.platform.integration.cohort.application.ledger.CohortLedger
import net.blueshell.api.platform.integration.cohort.persistence.Cohort
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.cohort.port.`in`.CohortMembershipSync
import net.blueshell.api.platform.integration.cohort.port.`in`.SyncCohortMembershipIntent
import net.blueshell.api.platform.integration.cohort.port.out.CohortPort
import net.blueshell.api.platform.integration.cohort.port.out.CohortPortRegistry
import net.blueshell.api.platform.integration.sync.application.ExternalIdMappingService
import net.blueshell.api.platform.integration.sync.application.ExternalIdMappingService.Companion.USER_AGGREGATE
import net.blueshell.api.platform.integration.sync.port.TargetSystem
import net.blueshell.api.shared.job.CohortJobs
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.NonRetryableJobException
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime

/**
 * Application implementation of the [CohortMembershipSync] inbound
 * port. Drives one `(user, cohort)` sync end-to-end: resolves the user
 * external id, resolves the cohort target id through [CohortTargetIds],
 * picks the right [CohortPort] for the cohort's system, and asks the
 * outbound port to apply the change.
 *
 * Recovery semantics:
 * - `ADD` with no user external id enqueues `SyncContact` and throws
 *   a retryable exception so the retry picks up after the contact
 *   has materialised externally.
 * - `ADD` with no cohort target id enqueues `cohort.materialize-target`
 *   and throws a retryable exception — it never creates the target
 *   itself, so two racing ADDs cannot create two remote targets.
 * - `REMOVE` with no external state on either side is a no-op —
 *   there is nothing to converge to.
 */
@Service
class CohortMembershipSyncService(
    private val cohorts: CohortRepository,
    private val ledger: CohortLedger,
    private val registry: CohortPortRegistry,
    private val externalIds: ExternalIdMappingService,
    private val targetIds: CohortTargetIds,
    private val jobs: TrackedJobDispatcher,
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
        val port = registry.require(system)

        when (intent) {
            SyncCohortMembershipIntent.ADD -> add(userId, cohort, port)
            SyncCohortMembershipIntent.REMOVE -> remove(userId, cohort, port)
        }
    }

    private fun add(userId: Long, cohort: Cohort, port: CohortPort) {
        val cohortId = cohort.id!!
        val system = cohort.system
        val externalUserId = externalIds.find(USER_AGGREGATE, userId, system)?.externalId
        if (externalUserId == null) {
            jobs.enqueue(ContactJobs.SyncContact, ContactJobs.SyncContactPayload(userId))
            throw CohortMembershipNotReadyException(
                "user $userId has no $system external id — enqueued SyncContact, will retry",
            )
        }
        val externalCohortId = targetIds.find(cohort)
        if (externalCohortId == null) {
            jobs.enqueue(CohortJobs.MaterializeCohortTarget, CohortJobs.MaterializeCohortTargetPayload(cohortId))
            throw CohortMembershipNotReadyException(
                "cohort $cohortId has no $system target — enqueued materialize-target, will retry",
            )
        }
        outsideTransaction.executeWithoutResult { port.addMember(externalUserId, externalCohortId) }

        // Stamp the ledger so the desired row reads as synced. This is the
        // primary path to healthy; reconcile only verifies afterwards.
        if (!ledger.markPushed(cohortId, userId, externalUserId, LocalDateTime.now())) {
            log.warn("Pushed user {} to {} cohort {} but its desired row is gone — not stamping", userId, system, cohortId)
        }
        log.debug("Added user {} to {} cohort {} (ext={})", userId, system, cohortId, externalCohortId)
    }

    private fun remove(userId: Long, cohort: Cohort, port: CohortPort) {
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
        outsideTransaction.executeWithoutResult { port.removeMember(externalUserId, externalCohortId) }
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
