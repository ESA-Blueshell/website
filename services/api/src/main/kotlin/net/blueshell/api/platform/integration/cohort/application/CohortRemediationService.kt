package net.blueshell.api.platform.integration.cohort.application

import net.blueshell.api.platform.integration.cohort.persistence.CohortMember
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortMemberRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortSubjectRepository
import net.blueshell.api.platform.integration.cohort.port.`in`.CohortRemediation
import net.blueshell.api.platform.integration.cohort.port.out.CohortPortRegistry
import net.blueshell.api.platform.integration.sync.application.ExternalIdMappingService
import net.blueshell.api.platform.integration.sync.persistence.ExternalIdMapping
import net.blueshell.api.platform.integration.sync.port.TargetSystem
import net.blueshell.api.shared.job.CohortJobs
import net.blueshell.api.shared.job.NonRetryableJobException
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class CohortRemediationService(
    private val cohortRepo: CohortRepository,
    private val subjectRepo: CohortSubjectRepository,
    private val memberRepo: CohortMemberRepository,
    private val externalIds: ExternalIdMappingService,
    private val registry: CohortPortRegistry,
    private val jobs: TrackedJobDispatcher,
) : CohortRemediation {

    @Transactional
    override fun linkUser(userId: Long, system: TargetSystem, externalUserId: String): ExternalIdMapping =
        externalIds.linkUser(userId, system, externalUserId)

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
    @Transactional
    override fun reconcileList(cohortId: Long) {
        val cohort = cohortRepo.findById(cohortId).orElseThrow {
            NonRetryableJobException("Cohort $cohortId not found")
        }
        val subject = cohort.subjectId?.let { subjectRepo.findById(it).orElse(null) }
            ?: throw NonRetryableJobException("Cohort $cohortId has no subject_id")
        val system = TargetSystem.valueOf(cohort.system)
        val port = registry.require(system)
        val externalCohortId = externalIds.find(COHORT_AGGREGATE, cohortId, cohort.system)?.externalId
            ?: throw NonRetryableJobException("Cohort $cohortId has no external id on $system")

        // 1. Fetch full external list (single network call).
        val remote = port.listMembers(externalCohortId)
        val remoteByExtId = remote.associateBy { it.externalUserId }
        val remoteExtIds = remoteByExtId.keys

        // 2. Load desired rows and resolve their external ids.
        val desiredRows = memberRepo.findAllByCohortIdAndUserIdIsNotNull(cohortId)
        val desiredUserIds = desiredRows.map { it.userId!! }.toSet()
        val extIdByUserId = externalIds
            .findBatch(USER_AGGREGATE, desiredUserIds, system.name)
            .associate { it.aggregateId to it.externalId }

        val now = LocalDateTime.now()

        // 3. Update desired rows and collect confirmed external ids.
        val confirmedExtIds = mutableSetOf<String>()
        desiredRows.forEach { row ->
            val extId = extIdByUserId[row.userId]
            if (extId != null && extId in remoteExtIds) {
                // Confirmed present: stamp the ledger.
                row.externalUserId = extId
                row.observedAt = now
                row.label = remoteByExtId[extId]?.label
                confirmedExtIds.add(extId)
                memberRepo.save(row)
            } else if (row.observedAt != null) {
                // Was confirmed before but no longer present remotely.
                row.observedAt = null
                memberRepo.save(row)
            }
        }

        // 4. Enqueue ADD for desired rows missing from the remote snapshot.
        desiredRows.forEach { row ->
            val extId = extIdByUserId[row.userId]
            if (extId == null) {
                jobs.enqueue(
                    CohortJobs.SyncCohortMembership,
                    CohortJobs.SyncCohortMembershipPayload(
                        row.userId!!,
                        cohortId,
                        net.blueshell.api.platform.integration.cohort.port.`in`.SyncCohortMembershipIntent.ADD,
                    ),
                )
            } else if (extId !in remoteExtIds) {
                jobs.enqueue(
                    CohortJobs.SyncCohortMembership,
                    CohortJobs.SyncCohortMembershipPayload(
                        row.userId!!,
                        cohortId,
                        net.blueshell.api.platform.integration.cohort.port.`in`.SyncCohortMembershipIntent.ADD,
                    ),
                )
            }
        }

        // 5. Upsert stranger rows for remote ids not confirmed as desired.
        val strangerExtIds = remoteExtIds - confirmedExtIds
        strangerExtIds.forEach { extId ->
            val existing = memberRepo.findByCohortIdAndExternalUserIdAndUserIdIsNull(cohortId, extId)
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

        // 6. Soft-delete stranger rows for remote ids no longer present.
        memberRepo.findAllByCohortIdAndUserIdIsNull(cohortId)
            .filter { it.externalUserId !in remoteExtIds }
            .forEach { memberRepo.delete(it) }
    }

    companion object {
        private const val COHORT_AGGREGATE = "COHORT"
        private const val USER_AGGREGATE = "USER"
    }
}
