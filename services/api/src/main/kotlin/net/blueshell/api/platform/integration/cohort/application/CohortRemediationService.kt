package net.blueshell.api.platform.integration.cohort.application

import net.blueshell.api.platform.integration.cohort.application.ledger.CohortLedger
import net.blueshell.api.platform.integration.cohort.application.ledger.CohortLedger.DesiredConfirmation
import net.blueshell.api.shared.enums.CohortMemberState
import net.blueshell.api.platform.integration.cohort.persistence.state
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortMemberRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortSubjectRepository
import net.blueshell.api.platform.integration.cohort.port.`in`.CohortRemediation
import net.blueshell.api.platform.integration.cohort.port.`in`.CohortRepairResult
import net.blueshell.api.platform.integration.cohort.port.`in`.SyncCohortMembershipIntent
import net.blueshell.api.platform.integration.cohort.port.out.CohortPortRegistry
import net.blueshell.api.platform.integration.cohort.port.out.MemberRef
import net.blueshell.api.platform.integration.sync.application.ExternalIdMappingService
import net.blueshell.api.platform.integration.sync.application.ExternalIdMappingService.Companion.USER_AGGREGATE
import net.blueshell.api.platform.integration.sync.persistence.ExternalIdMapping
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.platform.integration.cohort.application.CohortJobs
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
    private val cohortRepo: CohortRepository,
    private val subjectRepo: CohortSubjectRepository,
    private val memberRepo: CohortMemberRepository,
    private val ledger: CohortLedger,
    private val externalIds: ExternalIdMappingService,
    private val targetIds: CohortTargetIds,
    private val registry: CohortPortRegistry,
    private val jobs: TrackedJobDispatcher,
    transactionManager: PlatformTransactionManager,
) : CohortRemediation {

    private val readOnlyTransaction = TransactionTemplate(transactionManager).apply { isReadOnly = true }
    private val writeTransaction = TransactionTemplate(transactionManager)

    // Suspends any active transaction (notably the one AbstractJsonJobHandler
    // opens) so provider HTTP calls hold no DB connection — ADR-006/ADR-023.
    private val outsideTransaction = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_NOT_SUPPORTED
    }

    @Transactional
    override fun linkUser(
        subjectId: Long,
        userId: Long,
        system: TargetSystem,
        externalUserId: String,
    ): ExternalIdMapping {
        val mapping = externalIds.linkUser(userId, system, externalUserId)
        foldLinkedUser(subjectId, userId, system, externalUserId)
        return mapping
    }

    @Transactional
    override fun removeExternalMember(cohortId: Long, externalUserId: String) {
        val cohort = cohortRepo.findById(cohortId).orElseThrow {
            NonRetryableJobException("Cohort $cohortId not found")
        }
        val system = TargetSystem.valueOf(cohort.system)
        val externalCohortId = targetIds.require(cohort)

        outsideTransaction.executeWithoutResult { registry.require(system).removeMember(externalUserId, externalCohortId) }
        ledger.removeStranger(cohortId, externalUserId)
    }

    /**
     * Fetches the full external member list for [cohortId] and reconciles
     * the ledger against it. One network call per run; runs the fetch
     * outside any DB transaction.
     */
    override fun verifyCohort(cohortId: Long) {
        val plan = readOnlyTransaction.execute { loadPlan(cohortId) }!!
        val remote = outsideTransaction.execute { registry.require(plan.system).listMembers(plan.externalCohortId) }!!
        writeTransaction.executeWithoutResult { applySnapshot(plan, remote) }
    }

    override fun repairMissingAdds(cohortId: Long): CohortRepairResult {
        return writeTransaction.execute {
            val cohort = cohortRepo.findById(cohortId).orElseThrow {
                NonRetryableJobException("Cohort $cohortId not found")
            }
            targetIds.require(cohort)
            val rows = memberRepo.findAllByCohortIdAndUserIdIsNotNull(cohortId)
                .filter { it.syncedAt == null }
            rows.forEach { row ->
                jobs.runAsync(
                    CohortJobs.SyncCohortMembership,
                    CohortJobs.SyncCohortMembershipPayload(row.userId!!, cohortId, SyncCohortMembershipIntent.ADD),
                )
            }
            CohortRepairResult(cohortId, rows.size)
        }!!
    }

    private fun loadPlan(cohortId: Long): ReconcilePlan {
        val cohort = cohortRepo.findById(cohortId).orElseThrow {
            NonRetryableJobException("Cohort $cohortId not found")
        }
        val subjectId = cohort.subjectId
            ?: throw NonRetryableJobException("Cohort $cohortId has no subject_id")
        subjectRepo.findById(subjectId).orElseThrow {
            NonRetryableJobException("Cohort $cohortId references missing subject $subjectId")
        }
        val system = TargetSystem.valueOf(cohort.system)
        val externalCohortId = targetIds.require(cohort)

        return ReconcilePlan(cohortId, subjectId, system, externalCohortId)
    }

    private fun applySnapshot(plan: ReconcilePlan, remote: List<MemberRef>) {
        val cohort = cohortRepo.findById(plan.cohortId).orElseThrow {
            NonRetryableJobException("Cohort ${plan.cohortId} not found")
        }
        val subject = subjectRepo.findById(plan.subjectId).orElseThrow {
            NonRetryableJobException("Cohort ${plan.cohortId} references missing subject ${plan.subjectId}")
        }
        val remoteByExtId = remote.associateBy { it.externalUserId }
        val now = LocalDateTime.now()
        val desiredRows = memberRepo.findAllByCohortIdAndUserIdIsNotNull(plan.cohortId)
        val externalIdByUserId = loadCurrentExternalIds(plan.cohortId, desiredRows, plan.system)

        val confirmed = confirmPresentDesiredRows(plan, desiredRows, externalIdByUserId, remoteByExtId, now)
        demoteVanishedDesiredRows(plan, desiredRows, externalIdByUserId, remoteByExtId.keys)
        enqueueFollowUpsForMissing(plan, desiredRows, externalIdByUserId, remoteByExtId.keys)
        reconcileStrangers(cohort, subject, remoteByExtId, confirmed, now)
    }

    private fun loadCurrentExternalIds(
        cohortId: Long,
        desiredRows: List<net.blueshell.api.platform.integration.cohort.persistence.CohortMember>,
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
                        cohortId,
                        externalId,
                        mappings.map { it.aggregateId },
                    )
                }
            }
            .filterValues { it.size == 1 }
            .values
            .flatten()
            .associate { it.aggregateId to it.externalId!! }
    }

    /** Desired rows present in the snapshot: confirm and collapse any matching stranger. */
    private fun confirmPresentDesiredRows(
        plan: ReconcilePlan,
        desiredRows: List<net.blueshell.api.platform.integration.cohort.persistence.CohortMember>,
        externalIdByUserId: Map<Long, String>,
        remoteByExtId: Map<String, MemberRef>,
        now: LocalDateTime,
    ): Set<String> {
        val confirmations = desiredRows.mapNotNull { row ->
            val extId = externalIdByUserId[row.userId] ?: return@mapNotNull null
            val remoteMember = remoteByExtId[extId] ?: return@mapNotNull null
            DesiredConfirmation(row, extId, remoteMember.label)
        }
        return ledger.markVerified(confirmations, now)
    }

    /** Desired rows that claimed sync/verify but are now absent: demote so they re-bucket as missing. */
    private fun demoteVanishedDesiredRows(
        plan: ReconcilePlan,
        desiredRows: List<net.blueshell.api.platform.integration.cohort.persistence.CohortMember>,
        externalIdByUserId: Map<Long, String>,
        remoteExtIds: Set<String>,
    ) {
        desiredRows.forEach { row ->
            val extId = externalIdByUserId[row.userId]
            val absent = extId == null || extId !in remoteExtIds
            if (absent && (row.state == CohortMemberState.SYNCED || row.state == CohortMemberState.VERIFIED)) {
                ledger.markDrifted(row)
            }
        }
    }

    /** Desired rows absent remotely: materialise the contact, or re-push. */
    private fun enqueueFollowUpsForMissing(
        plan: ReconcilePlan,
        desiredRows: List<net.blueshell.api.platform.integration.cohort.persistence.CohortMember>,
        externalIdByUserId: Map<Long, String>,
        remoteExtIds: Set<String>,
    ) {
        desiredRows.forEach { row ->
            val extId = externalIdByUserId[row.userId]
            if (extId == null) {
                jobs.runAsync(ContactJobs.SyncContact, ContactJobs.SyncContactPayload(row.userId!!))
            } else if (extId !in remoteExtIds) {
                jobs.runAsync(
                    CohortJobs.SyncCohortMembership,
                    CohortJobs.SyncCohortMembershipPayload(row.userId!!, plan.cohortId, SyncCohortMembershipIntent.ADD),
                )
            }
        }
    }

    /** Remote ids with no desired owner become strangers; strangers gone remotely are removed. */
    private fun reconcileStrangers(
        cohort: net.blueshell.api.platform.integration.cohort.persistence.Cohort,
        subject: net.blueshell.api.platform.integration.cohort.persistence.CohortSubject,
        remoteByExtId: Map<String, MemberRef>,
        confirmedExtIds: Set<String>,
        now: LocalDateTime,
    ) {
        (remoteByExtId.keys - confirmedExtIds).forEach { extId ->
            ledger.upsertStranger(cohort, subject, extId, remoteByExtId[extId]?.label, now)
        }
        memberRepo.findAllByCohortIdAndUserIdIsNull(cohort.id!!)
            .filter { it.externalUserId !in remoteByExtId.keys }
            .forEach { ledger.removeStranger(it) }
    }

    private fun foldLinkedUser(
        subjectId: Long,
        userId: Long,
        system: TargetSystem,
        externalUserId: String,
    ) {
        val cohort = cohortRepo.findBySubjectIdAndSystem(subjectId, system.name) ?: return
        val cohortId = cohort.id ?: return
        val stranger = memberRepo.findByCohortIdAndExternalUserIdAndUserIdIsNull(cohortId, externalUserId) ?: return
        val desired = memberRepo.findByCohortIdAndUserId(cohortId, userId) ?: return
        ledger.foldStrangerIntoDesired(desired, stranger)
    }

    private data class ReconcilePlan(
        val cohortId: Long,
        val subjectId: Long,
        val system: TargetSystem,
        val externalCohortId: String,
    )

    companion object {
        private val log = LoggerFactory.getLogger(CohortRemediationService::class.java)
    }
}
