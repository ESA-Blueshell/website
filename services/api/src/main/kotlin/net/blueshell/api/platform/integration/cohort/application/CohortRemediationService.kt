package net.blueshell.api.platform.integration.cohort.application

import net.blueshell.api.platform.integration.cohort.persistence.ExternalCohortMember
import net.blueshell.api.platform.integration.cohort.persistence.ExternalCohortMemberId
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortMemberRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.ExternalCohortMemberRepository
import net.blueshell.api.platform.integration.cohort.port.`in`.CohortRemediation
import net.blueshell.api.platform.integration.cohort.port.`in`.SyncCohortMembershipIntent
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
    private val cohortMemberRepo: CohortMemberRepository,
    private val externalMemberRepo: ExternalCohortMemberRepository,
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
        // Delete the shadow row immediately so the drift panel reflects the
        // change without waiting for the next reconcile run.
        externalMemberRepo.deleteRow(cohortId, externalUserId)
    }

    /**
     * Fetches the full external member list for [cohortId], upserts the
     * shadow table, then fans out narrow ADD/REMOVE jobs for each
     * discrepancy. One network call per reconcile run; each downstream
     * job is a single-operation retry unit.
     */
    @Transactional
    override fun reconcileList(cohortId: Long) {
        val cohort = cohortRepo.findById(cohortId).orElseThrow {
            NonRetryableJobException("Cohort $cohortId not found")
        }
        val system = TargetSystem.valueOf(cohort.system)
        val port = registry.require(system)
        val externalCohortId = externalIds.find(COHORT_AGGREGATE, cohortId, cohort.system)?.externalId
            ?: throw NonRetryableJobException("Cohort $cohortId has no external id on $system")

        // 1. Fetch the full external list (one network call per reconcile run).
        val fetched = port.listMembers(externalCohortId)
        val fetchedIds = fetched.map { it.externalUserId }.toSet()

        // 2. Update shadow table: upsert present rows, hard-delete absent rows.
        val now = LocalDateTime.now()
        fetched.forEach { ref ->
            externalMemberRepo.save(
                ExternalCohortMember(
                    id = ExternalCohortMemberId(cohortId, ref.externalUserId),
                    label = ref.label,
                    observedAt = now,
                )
            )
        }
        if (fetchedIds.isNotEmpty()) {
            externalMemberRepo.deleteStaleRows(cohortId, fetchedIds)
        } else {
            externalMemberRepo.deleteAllByCohortId(cohortId)
        }

        // 3. Desired local members and their external ids.
        val desiredUserIds = cohortMemberRepo.findAllByCohortId(cohortId).map { it.userId }.toSet()
        val desiredExternalIds = externalIds
            .findBatch(USER_AGGREGATE, desiredUserIds, system.name)
            .associate { it.aggregateId to it.externalId }

        // 4. Enqueue ADD for desired members absent from the external snapshot.
        desiredUserIds.forEach { userId ->
            val extId = desiredExternalIds[userId]
            if (extId == null || extId !in fetchedIds) {
                jobs.enqueue(
                    CohortJobs.SyncCohortMembership,
                    CohortJobs.SyncCohortMembershipPayload(userId, cohortId, SyncCohortMembershipIntent.ADD),
                )
            }
        }

        // 5. Enqueue REMOVE for external rows that are not desired locally.
        val desiredExternalSet = desiredExternalIds.values.toSet()
        fetchedIds
            .filter { it !in desiredExternalSet }
            .forEach { extUserId ->
                jobs.enqueue(
                    CohortJobs.RemoveExternalMember,
                    CohortJobs.RemoveExternalMemberPayload(cohortId, extUserId),
                )
            }
    }

    companion object {
        private const val COHORT_AGGREGATE = "COHORT"
        private const val USER_AGGREGATE = "USER"
    }
}
