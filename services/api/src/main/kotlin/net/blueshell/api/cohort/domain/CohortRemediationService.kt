package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.domain.CohortLedger.DesiredConfirmation
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.DriftResolution
import net.blueshell.api.cohort.persistence.DriftResolutionAction
import net.blueshell.api.cohort.persistence.DriftResolutionRepository
import net.blueshell.api.cohort.persistence.TargetMemberRepository
import net.blueshell.api.cohort.persistence.TargetReconcileRun
import net.blueshell.api.cohort.persistence.TargetReconcileRunRepository
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.cohort.persistence.state
import net.blueshell.api.contact.api.ContactJobs
import net.blueshell.api.shared.enums.TargetMemberState
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.shared.job.NonRetryableJobException
import net.blueshell.api.sync.api.ExternalIdMappingService
import net.blueshell.api.sync.api.ExternalIdMappingService.Companion.USER_AGGREGATE
import net.blueshell.api.sync.persistence.ExternalIdMapping
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.time.LocalDateTime

/**
 * Operator/scheduled remediation against external membership. The list
 * reconcile is now a *verifier*: the per-member sync path establishes
 * health (`syncedAt`), and this confirms it (`verifiedAt`), demotes
 * vanished members, and records strangers.
 *
 * All `cohort_member` writes go through [CohortLedger]; this service only
 * decides which transition applies and enqueues follow-up jobs.
 */
@Service
class CohortRemediationService(
    private val targetRepo: TargetRepository,
    private val cohortRepo: CohortRepository,
    private val memberRepo: TargetMemberRepository,
    private val ledger: CohortLedger,
    private val externalIds: ExternalIdMappingService,
    private val targetExternalIds: CohortTargetIds,
    private val strategies: TargetStrategies,
    private val jobs: JobQueue,
    private val runs: TargetReconcileRunRepository,
    private val resolutions: DriftResolutionRepository,
    transactionManager: PlatformTransactionManager,
) : CohortRemediation {
    private val readOnlyTransaction = TransactionTemplate(transactionManager).apply { isReadOnly = true }
    private val writeTransaction = TransactionTemplate(transactionManager)

    // Suspends any active transaction (notably the one AbstractJsonJobHandler
    // opens) so provider HTTP calls hold no DB connection — ADR-006/ADR-023.
    private val outsideTransaction =
        TransactionTemplate(transactionManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_NOT_SUPPORTED
        }

    @Transactional
    override fun linkUser(
        cohortId: Long,
        userId: Long,
        system: TargetSystem,
        externalUserId: String,
    ): ExternalIdMapping {
        val mapping = externalIds.linkUser(userId, system, externalUserId)
        foldLinkedUser(cohortId, userId, system, externalUserId)
        return mapping
    }

    @Transactional
    override fun removeExternalMember(
        targetId: Long,
        externalUserId: String,
    ) {
        val target =
            targetRepo.findById(targetId).orElseThrow {
                NonRetryableJobException("Target $targetId not found")
            }
        val system = TargetSystem.valueOf(target.system)
        val externalTargetId = targetExternalIds.require(target)

        val strategy = strategies.requireForJob(system)
        outsideTransaction.executeWithoutResult { strategy.remove(strategy.handle(externalTargetId), externalUserId) }
        ledger.removeStranger(targetId, externalUserId)
    }

    /**
     * Fetches the full external member list for [targetId] and reconciles
     * the ledger against it. One network call per run; runs the fetch
     * outside any DB transaction.
     */
    override fun verifyTarget(
        targetId: Long,
        trigger: JobTrigger?,
    ) {
        val startedAt = Instant.now()
        val plan = readOnlyTransaction.execute { loadPlan(targetId) }
        val strategy = strategies.requireForJob(plan.system)
        val remote = outsideTransaction.execute { strategy.members(strategy.handle(plan.externalTargetId)) }
        writeTransaction.executeWithoutResult {
            applySnapshot(plan, remote)
            recordRun(targetId, startedAt, trigger)
            enforce(targetId)
        }
    }

    // An enforced target loses its theirs-only people on every reconcile; ours-only drift is
    // never resolved by itself.
    private fun enforce(targetId: Long) {
        if (targetRepo.findById(targetId).orElse(null)?.enforced != true) return
        val strangers = memberRepo.findAllByTargetIdAndUserIdIsNull(targetId).filter { it.externalUserId != null }
        strangers.forEach { row ->
            jobs.runAsync(
                CohortJobs.RemoveExternalMember,
                CohortJobs.RemoveExternalMemberPayload(targetId, row.externalUserId!!),
                JobTrigger.ANOTHER_JOB,
            )
        }
        val at = Instant.now()
        resolutions.saveAll(
            strangers.map { DriftResolution(targetId, DriftResolutionAction.ENFORCED_REMOVE, null, it.externalUserId, it.label, null, at) },
        )
    }

    // The ledger after the snapshot is the drift: confirmed present, ours only, theirs only.
    private fun recordRun(
        targetId: Long,
        startedAt: Instant,
        trigger: JobTrigger?,
    ) {
        val states = memberRepo.findAllByTargetId(targetId).groupingBy { it.state }.eachCount()
        runs.save(
            TargetReconcileRun(
                targetId = targetId,
                startedAt = startedAt,
                trigger = trigger,
                inSync = states[TargetMemberState.VERIFIED] ?: 0,
                oursOnly = (states[TargetMemberState.DESIRED] ?: 0) + (states[TargetMemberState.SYNCED] ?: 0),
                theirsOnly = states[TargetMemberState.STRANGER] ?: 0,
            ),
        )
    }

    private fun loadPlan(targetId: Long): ReconcilePlan {
        val target =
            targetRepo.findById(targetId).orElseThrow {
                NonRetryableJobException("Target $targetId not found")
            }
        val cohortId =
            target.cohortId
                ?: throw NonRetryableJobException("Target $targetId has no cohort")
        cohortRepo.findById(cohortId).orElseThrow {
            NonRetryableJobException("Target $targetId references missing cohort $cohortId")
        }
        val system = TargetSystem.valueOf(target.system)
        val externalTargetId = targetExternalIds.require(target)

        return ReconcilePlan(targetId, cohortId, system, externalTargetId)
    }

    private fun applySnapshot(
        plan: ReconcilePlan,
        remote: List<ExternalMember>,
    ) {
        val target =
            targetRepo.findById(plan.targetId).orElseThrow {
                NonRetryableJobException("Target ${plan.targetId} not found")
            }
        val cohort =
            cohortRepo.findById(plan.cohortId).orElseThrow {
                NonRetryableJobException("Target ${plan.targetId} references missing cohort ${plan.cohortId}")
            }
        val remoteByExtId = remote.associateBy { it.externalUserId }
        val now = LocalDateTime.now()
        val desiredRows = memberRepo.findAllByTargetIdAndUserIdIsNotNull(plan.targetId)
        val externalIdByUserId = loadCurrentExternalIds(plan.targetId, desiredRows, plan.system)

        val confirmed = confirmPresentDesiredRows(desiredRows, externalIdByUserId, remoteByExtId, now)
        demoteVanishedDesiredRows(desiredRows, externalIdByUserId, remoteByExtId.keys)
        enqueueFollowUpsForMissing(plan, desiredRows, externalIdByUserId, remoteByExtId.keys)
        reconcileStrangers(target, cohort, remoteByExtId, confirmed, now)
    }

    private fun loadCurrentExternalIds(
        targetId: Long,
        desiredRows: List<net.blueshell.api.cohort.persistence.TargetMember>,
        system: TargetSystem,
    ): Map<Long, String> {
        val desiredUserIds = desiredRows.mapNotNull { it.userId }.toSet()
        return externalIds
            .findBatch(USER_AGGREGATE, desiredUserIds, system.name)
            .filter { !it.externalId.isNullOrBlank() }
            .groupBy { it.externalId }
            .also { grouped ->
                grouped.filterValues { it.size > 1 }.forEach { (externalId, mappings) ->
                    log.warn(
                        "Ignoring duplicate external id mapping for cohort {} and external id {} across user ids {}",
                        targetId,
                        externalId,
                        mappings.map { it.aggregateId },
                    )
                }
            }.filterValues { it.size == 1 }
            .values
            .flatten()
            .associate { it.aggregateId to it.externalId!! }
    }

    /** Desired rows present in the snapshot: confirm and collapse any matching stranger. */
    private fun confirmPresentDesiredRows(
        desiredRows: List<net.blueshell.api.cohort.persistence.TargetMember>,
        externalIdByUserId: Map<Long, String>,
        remoteByExtId: Map<String, ExternalMember>,
        now: LocalDateTime,
    ): Set<String> {
        val confirmations =
            desiredRows.mapNotNull { row ->
                val extId = externalIdByUserId[row.userId] ?: return@mapNotNull null
                val remoteMember = remoteByExtId[extId] ?: return@mapNotNull null
                DesiredConfirmation(row, extId, remoteMember.label)
            }
        return ledger.markVerified(confirmations, now)
    }

    /** Desired rows that claimed sync/verify but are now absent: demote so they re-bucket as missing. */
    private fun demoteVanishedDesiredRows(
        desiredRows: List<net.blueshell.api.cohort.persistence.TargetMember>,
        externalIdByUserId: Map<Long, String>,
        remoteExtIds: Set<String>,
    ) {
        desiredRows.forEach { row ->
            val extId = externalIdByUserId[row.userId]
            val absent = extId == null || extId !in remoteExtIds
            if (absent && (row.state == TargetMemberState.SYNCED || row.state == TargetMemberState.VERIFIED)) {
                ledger.markDrifted(row)
            }
        }
    }

    /** Desired rows absent remotely: materialise the contact, or re-push. */
    private fun enqueueFollowUpsForMissing(
        plan: ReconcilePlan,
        desiredRows: List<net.blueshell.api.cohort.persistence.TargetMember>,
        externalIdByUserId: Map<Long, String>,
        remoteExtIds: Set<String>,
    ) {
        desiredRows.forEach { row ->
            val extId = externalIdByUserId[row.userId]
            if (extId == null) {
                jobs.runAsync(ContactJobs.SyncContact, ContactJobs.SyncContactPayload(row.userId!!), JobTrigger.ANOTHER_JOB)
            } else if (extId !in remoteExtIds) {
                jobs.runAsync(
                    CohortJobs.SyncCohortMembership,
                    CohortJobs.SyncCohortMembershipPayload(row.userId!!, plan.targetId, SyncCohortMembershipIntent.ADD),
                    JobTrigger.ANOTHER_JOB,
                )
            }
        }
    }

    /** Remote ids with no desired owner become strangers; strangers gone remotely are removed. */
    private fun reconcileStrangers(
        target: net.blueshell.api.cohort.persistence.Target,
        cohort: net.blueshell.api.cohort.persistence.Cohort,
        remoteByExtId: Map<String, ExternalMember>,
        confirmedExtIds: Set<String>,
        now: LocalDateTime,
    ) {
        (remoteByExtId.keys - confirmedExtIds).forEach { extId ->
            ledger.upsertStranger(target, cohort, extId, remoteByExtId[extId]?.label, now)
        }
        memberRepo
            .findAllByTargetIdAndUserIdIsNull(target.id!!)
            .filter { it.externalUserId !in remoteByExtId.keys }
            .forEach { ledger.removeStranger(it) }
    }

    private fun foldLinkedUser(
        cohortId: Long,
        userId: Long,
        system: TargetSystem,
        externalUserId: String,
    ) {
        val target = targetRepo.findByCohortIdAndSystem(cohortId, system.name) ?: return
        val targetId = target.id ?: return
        val stranger = memberRepo.findByTargetIdAndExternalUserIdAndUserIdIsNull(targetId, externalUserId) ?: return
        val desired = memberRepo.findByTargetIdAndUserId(targetId, userId) ?: return
        ledger.foldStrangerIntoDesired(desired, stranger)
    }

    private data class ReconcilePlan(
        val targetId: Long,
        val cohortId: Long,
        val system: TargetSystem,
        val externalTargetId: String,
    )

    companion object {
        private val log = LoggerFactory.getLogger(CohortRemediationService::class.java)
    }
}
