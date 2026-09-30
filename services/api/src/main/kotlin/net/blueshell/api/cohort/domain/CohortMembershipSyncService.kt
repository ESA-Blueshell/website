package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.Target
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.contact.api.ContactJobs
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.shared.job.NonRetryableJobException
import net.blueshell.api.sync.api.ExternalIdMappingService
import net.blueshell.api.sync.api.ExternalIdMappingService.Companion.USER_AGGREGATE
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
    private val targets: TargetRepository,
    private val ledger: CohortLedger,
    private val strategies: TargetStrategies,
    private val externalIds: ExternalIdMappingService,
    private val targetExternalIds: CohortTargetIds,
    private val jobs: JobQueue,
    transactionManager: PlatformTransactionManager,
) {
    // Suspends the surrounding transaction (this service's own and the
    // @Transactional opened by AbstractJsonJobHandler) so the provider HTTP
    // call holds no DB connection and runs with no transaction active —
    // ADR-006/ADR-023. DB reads/writes stay in the suspended-and-resumed
    // outer transaction around it.
    private val outsideTransaction =
        TransactionTemplate(transactionManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_NOT_SUPPORTED
        }

    /**
     * Pushes one `(user, cohort)` membership to its external system. A caller hands over the
     * triple and never branches on what follows: a missing user sync is enqueued and retried, a
     * missing cohort target is terminal, and a REMOVE against absent external state is a no-op.
     * Answers why nothing was pushed, or null where it was.
     */
    @Transactional
    fun sync(
        userId: Long,
        targetId: Long,
        intent: SyncCohortMembershipIntent,
    ): String? {
        val target =
            targets.findById(targetId).orElseThrow {
                NonRetryableJobException("Target $targetId not found")
            }
        val system =
            runCatching { TargetSystem.valueOf(target.system) }.getOrElse {
                throw NonRetryableJobException("Target $targetId has unknown system '${target.system}'")
            }
        val strategy = strategies.requireForJob(system)

        return when (intent) {
            SyncCohortMembershipIntent.ADD -> {
                add(userId, target, strategy)
                null
            }
            SyncCohortMembershipIntent.REMOVE -> remove(userId, target, strategy)
        }
    }

    private fun add(
        userId: Long,
        target: Target,
        strategy: TargetStrategy,
    ) {
        val targetId = target.id!!
        val system = target.system
        val externalUserId = externalIds.find(USER_AGGREGATE, userId, system)?.externalId
        if (externalUserId == null) {
            jobs.runAsync(ContactJobs.SyncContact, ContactJobs.SyncContactPayload(userId), JobTrigger.ANOTHER_JOB)
            throw CohortMembershipNotReadyException(
                "user $userId has no $system external id — enqueued SyncContact, will retry",
            )
        }
        val externalTargetId = targetExternalIds.find(target)
        if (externalTargetId == null) {
            throw CohortTargetNotLinkedException(targetId, system)
        }
        outsideTransaction.executeWithoutResult { strategy.add(strategy.handle(externalTargetId), externalUserId) }

        // Stamp the ledger so the desired row reads as synced. This is the
        // primary path to healthy; reconcile only verifies afterwards.
        if (!ledger.markPushed(targetId, userId, externalUserId, LocalDateTime.now())) {
            log.warn("Pushed user {} to {} cohort {} but its desired row is gone — not stamping", userId, system, targetId)
        }
        log.debug("Added user {} to {} cohort {} (ext={})", userId, system, targetId, externalTargetId)
    }

    private fun remove(
        userId: Long,
        target: Target,
        strategy: TargetStrategy,
    ): String? {
        val targetId = target.id!!
        val system = target.system
        val externalUserId =
            externalIds.find(USER_AGGREGATE, userId, system)?.externalId
                ?: return "The user has no $system contact, so is on no $system list."
        val externalTargetId = targetExternalIds.find(target) ?: return "The cohort has no $system list linked."
        outsideTransaction.executeWithoutResult { strategy.remove(strategy.handle(externalTargetId), externalUserId) }
        log.debug("Removed user {} from {} cohort {} (ext={})", userId, system, targetId, externalTargetId)
        return null
    }

    companion object {
        private val log = LoggerFactory.getLogger(CohortMembershipSyncService::class.java)
    }
}

/**
 * Thrown when the (user, cohort) pair cannot be pushed yet because a
 * prerequisite (typically the user's external contact id) has not been
 * materialised. The exception is *not* annotated as non-retryable, so
 * the job framework re-runs the job after backoff once the prerequisite
 * job has had a chance to complete.
 */
class CohortMembershipNotReadyException(
    message: String,
) : RuntimeException(message)

class CohortTargetNotLinkedException(
    targetId: Long,
    system: String,
) : NonRetryableJobException(
        "cohort $targetId has no $system target — create or link an external target, then retry the membership job",
    )
