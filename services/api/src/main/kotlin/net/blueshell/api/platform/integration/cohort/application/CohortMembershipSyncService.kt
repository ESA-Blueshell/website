package net.blueshell.api.platform.integration.cohort.application

import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.cohort.port.`in`.CohortMembershipSync
import net.blueshell.api.platform.integration.cohort.port.`in`.SyncCohortMembershipIntent
import net.blueshell.api.platform.integration.cohort.port.out.CohortPort
import net.blueshell.api.platform.integration.cohort.port.out.CohortPortRegistry
import net.blueshell.api.platform.integration.sync.application.ExternalIdMappingService
import net.blueshell.api.platform.integration.sync.application.ExternalIdMappingService.Companion.COHORT_AGGREGATE
import net.blueshell.api.platform.integration.sync.application.ExternalIdMappingService.Companion.USER_AGGREGATE
import net.blueshell.api.platform.integration.sync.port.TargetSystem
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.NonRetryableJobException
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Application implementation of the [CohortMembershipSync] inbound
 * port. Drives one `(user, cohort)` sync end-to-end: resolves the
 * external ids through the unified `external_id_mapping` table,
 * picks the right [CohortPort] for the cohort's system, lazily
 * materialises the cohort's external counterpart on first `ADD`,
 * and asks the outbound port to apply the change.
 *
 * Recovery semantics:
 * - `ADD` with no user external id enqueues `SyncContact` and throws
 *   a retryable exception so the retry picks up after the contact
 *   has materialised externally.
 * - `REMOVE` with no external state on either side is a no-op —
 *   there is nothing to converge to.
 */
@Service
class CohortMembershipSyncService(
    private val cohorts: CohortRepository,
    private val registry: CohortPortRegistry,
    private val externalIds: ExternalIdMappingService,
    private val jobs: TrackedJobDispatcher,
) : CohortMembershipSync {

    override fun sync(userId: Long, cohortId: Long, intent: SyncCohortMembershipIntent) {
        val cohort = cohorts.findById(cohortId).orElseThrow {
            NonRetryableJobException("Cohort $cohortId not found")
        }
        val system = runCatching { TargetSystem.valueOf(cohort.system) }.getOrElse {
            throw NonRetryableJobException("Cohort $cohortId has unknown system '${cohort.system}'")
        }
        val port = registry.require(system)

        when (intent) {
            SyncCohortMembershipIntent.ADD -> add(userId, cohort.id!!, cohort.label, cohort.system, port)
            SyncCohortMembershipIntent.REMOVE -> remove(userId, cohort.id!!, cohort.system, port)
        }
    }

    private fun add(userId: Long, cohortId: Long, cohortLabel: String, system: String, port: CohortPort) {
        val externalUserId = externalIds.find(USER_AGGREGATE, userId, system)?.externalId
        if (externalUserId == null) {
            jobs.enqueue(ContactJobs.SyncContact, ContactJobs.SyncContactPayload(userId))
            throw CohortMembershipNotReadyException(
                "user $userId has no $system external id — enqueued SyncContact, will retry",
            )
        }
        val externalCohortId = findOrCreateExternalCohortId(cohortId, cohortLabel, system, port)
        port.addMember(externalUserId, externalCohortId)
        log.debug("Added user {} to {} cohort {} (ext={})", userId, system, cohortId, externalCohortId)
    }

    private fun remove(userId: Long, cohortId: Long, system: String, port: CohortPort) {
        val externalUserId = externalIds.find(USER_AGGREGATE, userId, system)?.externalId
        val externalCohortId = externalIds.find(COHORT_AGGREGATE, cohortId, system)?.externalId
        if (externalUserId == null || externalCohortId == null) {
            log.debug(
                "No $system external ids for user {} / cohort {} — skipping removal",
                userId, cohortId,
            )
            return
        }
        port.removeMember(externalUserId, externalCohortId)
        log.debug("Removed user {} from {} cohort {} (ext={})", userId, system, cohortId, externalCohortId)
    }

    /**
     * Returns the cohort's external id on `system`, creating the external
     * counterpart through [CohortPort.createCohort] on first use and
     * recording the new id in `external_id_mapping`. Mirrors the existing
     * `findOrCreateList` lazy-resolution idiom.
     */
    private fun findOrCreateExternalCohortId(
        cohortId: Long,
        cohortLabel: String,
        system: String,
        port: CohortPort,
    ): String {
        externalIds.find(COHORT_AGGREGATE, cohortId, system)?.externalId?.let { return it }
        log.info("Creating $system cohort {} ('{}') externally", cohortId, cohortLabel)
        val created = port.createCohort(cohortLabel)
        externalIds.upsert(COHORT_AGGREGATE, cohortId, system, created)
        return created
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
