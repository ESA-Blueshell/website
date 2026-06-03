package net.blueshell.api.platform.integration.cohort.application

import net.blueshell.api.platform.integration.cohort.persistence.CohortMember
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortMemberRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortSubjectRepository
import net.blueshell.api.platform.integration.cohort.port.`in`.CohortRemediation
import net.blueshell.api.platform.integration.cohort.port.`in`.SyncCohortMembershipIntent
import net.blueshell.api.platform.integration.cohort.port.out.CohortPortRegistry
import net.blueshell.api.platform.integration.cohort.port.out.MemberRef
import net.blueshell.api.platform.integration.sync.application.ExternalIdMappingService
import net.blueshell.api.platform.integration.sync.application.ExternalIdMappingService.Companion.COHORT_AGGREGATE
import net.blueshell.api.platform.integration.sync.application.ExternalIdMappingService.Companion.USER_AGGREGATE
import net.blueshell.api.platform.integration.sync.persistence.ExternalIdMapping
import net.blueshell.api.platform.integration.sync.port.TargetSystem
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.CohortJobs
import net.blueshell.api.shared.job.NonRetryableJobException
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime

@Service
class CohortRemediationService(
    private val cohortRepo: CohortRepository,
    private val subjectRepo: CohortSubjectRepository,
    private val memberRepo: CohortMemberRepository,
    private val externalIds: ExternalIdMappingService,
    private val registry: CohortPortRegistry,
    private val jobs: TrackedJobDispatcher,
    transactionManager: PlatformTransactionManager,
) : CohortRemediation {

    private val readOnlyTransaction = TransactionTemplate(transactionManager).apply { isReadOnly = true }
    private val writeTransaction = TransactionTemplate(transactionManager)

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
        val externalCohortId = externalIds.find(COHORT_AGGREGATE, cohortId, cohort.system)?.externalId
            ?: throw NonRetryableJobException("Cohort $cohortId has no external id on $system")

        registry.require(system).removeMember(externalUserId, externalCohortId)

        // Soft-delete the stranger row so the drift panel reflects the change.
        memberRepo.findByCohortIdAndExternalUserIdAndUserIdIsNull(cohortId, externalUserId)
            ?.let { memberRepo.delete(it) }
    }

    /**
     * Fetches the full external member list for [cohortId], updates the
     * unified [CohortMember] ledger, then enqueues narrow ADD jobs for
     * each discrepancy. One network call per run.
     *
     * Ledger semantics after this call:
     * - Desired rows whose external id is present remotely: stamped with
     *   `externalUserId` + `observedAt`.
     * - Desired rows absent remotely but with a known external id:
     *   `observedAt` cleared; ADD job enqueued.
     * - Desired rows with no external id yet: `SyncContact` enqueued.
     * - Remote ids not matching any desired row: upserted as stranger
     *   rows (`userId == null`).
     * - Extras are recorded only; removal is admin-triggered via
     *   remove-external, not automatic.
     */
    override fun reconcileList(cohortId: Long) {
        val plan = readOnlyTransaction.execute { loadPlan(cohortId) }

        val remote = registry.require(plan.system).listMembers(plan.externalCohortId)

        writeTransaction.executeWithoutResult {
            applySnapshot(plan, remote)
        }
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
        val externalCohortId = externalIds.find(COHORT_AGGREGATE, cohortId, cohort.system)?.externalId
            ?: throw NonRetryableJobException("Cohort $cohortId has no external id on $system")

        val desiredUserIds = memberRepo.findAllByCohortIdAndUserIdIsNotNull(cohortId)
            .mapNotNull { it.userId }
            .toSet()
        val extIdByUserId = externalIds
            .findBatch(USER_AGGREGATE, desiredUserIds, system.name)
            .associate { it.aggregateId to it.externalId }

        return ReconcilePlan(
            cohortId = cohortId,
            subjectId = subjectId,
            system = system,
            externalCohortId = externalCohortId,
            externalIdByUserId = extIdByUserId,
        )
    }

    private fun applySnapshot(
        plan: ReconcilePlan,
        remote: List<MemberRef>,
    ) {
        val cohort = cohortRepo.findById(plan.cohortId).orElseThrow {
            NonRetryableJobException("Cohort ${plan.cohortId} not found")
        }
        val subject = subjectRepo.findById(plan.subjectId).orElseThrow {
            NonRetryableJobException("Cohort ${plan.cohortId} references missing subject ${plan.subjectId}")
        }
        val remoteByExtId = remote.associateBy { it.externalUserId }
        val remoteExtIds = remoteByExtId.keys

        val now = LocalDateTime.now()
        val desiredRows = memberRepo.findAllByCohortIdAndUserIdIsNotNull(plan.cohortId)

        // Update desired rows and collect confirmed external ids.
        val confirmedExtIds = mutableSetOf<String>()
        desiredRows.forEach { row ->
            val extId = plan.externalIdByUserId[row.userId]
            if (extId != null && extId in remoteExtIds) {
                // Confirmed present: stamp the ledger.
                row.externalUserId = extId
                row.observedAt = now
                row.label = remoteByExtId[extId]?.label
                confirmedExtIds.add(extId)
                memberRepo.save(row)
                memberRepo.findByCohortIdAndExternalUserIdAndUserIdIsNull(plan.cohortId, extId)
                    ?.let { memberRepo.delete(it) }
            } else if (row.observedAt != null) {
                // Was confirmed before but no longer present remotely.
                row.observedAt = null
                memberRepo.save(row)
            }
        }

        // Enqueue follow-ups for desired rows missing from the remote snapshot.
        desiredRows.forEach { row ->
            val extId = plan.externalIdByUserId[row.userId]
            if (extId == null) {
                jobs.enqueue(
                    ContactJobs.SyncContact,
                    ContactJobs.SyncContactPayload(row.userId!!),
                )
            } else if (extId !in remoteExtIds) {
                jobs.enqueue(
                    CohortJobs.SyncCohortMembership,
                    CohortJobs.SyncCohortMembershipPayload(
                        row.userId!!,
                        plan.cohortId,
                        SyncCohortMembershipIntent.ADD,
                    ),
                )
            }
        }

        // Upsert stranger rows for remote ids not confirmed as desired.
        val strangerExtIds = remoteExtIds - confirmedExtIds
        strangerExtIds.forEach { extId ->
            val existing = memberRepo.findByCohortIdAndExternalUserIdAndUserIdIsNull(plan.cohortId, extId)
            if (existing != null) {
                existing.observedAt = now
                existing.label = remoteByExtId[extId]?.label
                memberRepo.save(existing)
            } else {
                memberRepo.save(
                    CohortMember(
                        cohort = cohort,
                        userId = null,
                        subject = subject,
                        externalUserId = extId,
                        observedAt = now,
                        label = remoteByExtId[extId]?.label,
                    ),
                )
            }
        }

        // Soft-delete stranger rows for remote ids no longer present.
        memberRepo.findAllByCohortIdAndUserIdIsNull(plan.cohortId)
            .filter { it.externalUserId !in remoteExtIds }
            .forEach { memberRepo.delete(it) }
    }

    private fun foldLinkedUser(
        subjectId: Long,
        userId: Long,
        system: TargetSystem,
        externalUserId: String,
    ) {
        val cohort = cohortRepo.findBySubjectIdAndSystem(subjectId, system.name) ?: return
        val cohortId = cohort.id ?: return
        val stranger = memberRepo.findByCohortIdAndExternalUserIdAndUserIdIsNull(cohortId, externalUserId)
            ?: return
        val desired = memberRepo.findByCohortIdAndUserId(cohortId, userId) ?: return

        desired.externalUserId = stranger.externalUserId
        desired.observedAt = stranger.observedAt
        desired.label = stranger.label
        memberRepo.save(desired)
        memberRepo.delete(stranger)
    }

    private data class ReconcilePlan(
        val cohortId: Long,
        val subjectId: Long,
        val system: TargetSystem,
        val externalCohortId: String,
        val externalIdByUserId: Map<Long, String?>,
    )

}
