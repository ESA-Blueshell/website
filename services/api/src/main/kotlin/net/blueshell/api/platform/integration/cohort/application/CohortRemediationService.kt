package net.blueshell.api.platform.integration.cohort.application

import net.blueshell.api.platform.integration.cohort.application.ledger.CohortLedger
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortMemberRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortSubjectRepository
import net.blueshell.api.platform.integration.cohort.port.`in`.CohortRemediation
import net.blueshell.api.platform.integration.cohort.port.`in`.SyncCohortMembershipIntent
import net.blueshell.api.platform.integration.cohort.port.out.CohortPortRegistry
import net.blueshell.api.platform.integration.cohort.port.out.MemberRef
import net.blueshell.api.platform.integration.sync.application.ExternalIdMappingService
import net.blueshell.api.platform.integration.sync.application.ExternalIdMappingService.Companion.USER_AGGREGATE
import net.blueshell.api.platform.integration.sync.persistence.ExternalIdMapping
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.CohortJobs
import net.blueshell.api.shared.job.NonRetryableJobException
import net.blueshell.api.shared.job.TrackedJobDispatcher
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
    // Pure, Spring-free collaborator (no bean needed). Defaulted so production
    // wiring requires no @Component on the reconciler; tests pass their own.
    private val reconciler: SnapshotReconciler = SnapshotReconciler(),
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

    /**
     * Orchestration only: reload the cohort/subject/rows fresh inside this
     * write tx, recompute `externalIdByUserId` HERE (not in [loadPlan]) so the
     * id map closes no stale-read window, build plain snapshots, delegate the
     * 4-way match to the pure [SnapshotReconciler], then replay the returned
     * action keys against the ledger (loading rows by id) and the dispatcher.
     */
    private fun applySnapshot(plan: ReconcilePlan, remote: List<MemberRef>) {
        val cohort = cohortRepo.findById(plan.cohortId).orElseThrow {
            NonRetryableJobException("Cohort ${plan.cohortId} not found")
        }
        val subject = subjectRepo.findById(plan.subjectId).orElseThrow {
            NonRetryableJobException("Cohort ${plan.cohortId} references missing subject ${plan.subjectId}")
        }
        val now = LocalDateTime.now()

        val desiredRows = memberRepo.findAllByCohortIdAndUserIdIsNotNull(plan.cohortId)
        val strangerRows = memberRepo.findAllByCohortIdAndUserIdIsNull(plan.cohortId)

        val externalIdByUserId = externalIds
            .findBatch(USER_AGGREGATE, desiredRows.mapNotNull { it.userId }.toSet(), plan.system.name)
            .associate { it.aggregateId to it.externalId }

        val rowById = (desiredRows + strangerRows).associateBy { it.id!! }
        val actions = reconciler.reconcile(
            desired = desiredRows.map { it.toSnapshot() },
            externalIdByUserId = externalIdByUserId,
            remote = remote,
            strangers = strangerRows.map { it.toSnapshot() },
            now = now,
        )

        actions.markVerified.forEach { ledger.markVerified(rowById.getValue(it.memberId), it.externalUserId, it.label, now) }
        actions.markDrifted.forEach { ledger.markDrifted(rowById.getValue(it)) }
        actions.enqueueContactSync.forEach {
            jobs.enqueue(ContactJobs.SyncContact, ContactJobs.SyncContactPayload(it))
        }
        actions.enqueueMembershipAdd.forEach {
            jobs.enqueue(
                CohortJobs.SyncCohortMembership,
                CohortJobs.SyncCohortMembershipPayload(it, plan.cohortId, SyncCohortMembershipIntent.ADD),
            )
        }
        actions.upsertStrangers.forEach { ledger.upsertStranger(cohort, subject, it.externalUserId, it.label, now) }
        actions.removeStrangers.forEach { removal ->
            removal.memberId?.let { ledger.removeStranger(rowById.getValue(it)) }
                ?: removal.externalUserId?.let { ledger.removeStranger(plan.cohortId, it) }
        }
    }

    private fun net.blueshell.api.platform.integration.cohort.persistence.CohortMember.toSnapshot() =
        SnapshotReconciler.MemberSnapshot(
            memberId = id!!,
            userId = userId,
            externalUserId = externalUserId,
            state = state,
            label = label,
        )

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
}
