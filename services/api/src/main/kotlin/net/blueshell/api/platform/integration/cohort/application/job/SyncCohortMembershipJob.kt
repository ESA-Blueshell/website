package net.blueshell.api.platform.integration.cohort.application.job

import net.blueshell.api.platform.integration.cohort.adapter.CohortAdapter
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.platform.integration.sync.application.ExternalIdMappingService
import net.blueshell.api.platform.integration.sync.port.TargetSystem
import net.blueshell.api.shared.job.CohortJobs
import net.blueshell.api.shared.job.CohortJobs.SyncCohortMembershipIntent
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.NonRetryableJobException
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Pushes one `(user, cohort)` membership to one external system.
 *
 * Resolves both ids through `external_id_mapping` (the unified
 * aggregate-to-external-id table) so cohort sync doesn't need to read
 * the legacy `Contact.externalId` column. If the user has no external
 * mapping yet (typically because `SyncContact` has not run for them),
 * the job enqueues `SyncContact` and throws a retryable exception so
 * the retry picks up after the contact has materialised externally —
 * same recovery pattern as `SyncListMembershipCommandHandler`.
 *
 * On `ADD` the cohort's external counterpart is lazily created via
 * `adapter.createCohort(...)` on first use and its id stored back in
 * `external_id_mapping` so subsequent runs are a single lookup.
 * `REMOVE` calls with no external mapping on either side are a no-op
 * (there is no external state to converge to).
 */
@Component
class SyncCohortMembershipJob(
    objectMapper: ObjectMapper,
    private val cohorts: CohortRepository,
    private val adapters: List<CohortAdapter>,
    private val externalIds: ExternalIdMappingService,
    private val jobs: TrackedJobDispatcher,
) : AbstractJsonJobHandler<CohortJobs.SyncCohortMembershipPayload>(
    objectMapper,
    CohortJobs.SyncCohortMembership.payloadType,
) {
    override val jobType: String = CohortJobs.SyncCohortMembership.type

    override fun handlePayload(payload: CohortJobs.SyncCohortMembershipPayload) {
        val cohort = cohorts.findById(payload.cohortId).orElseThrow {
            NonRetryableJobException("Cohort ${payload.cohortId} not found")
        }
        val system = runCatching { TargetSystem.valueOf(cohort.system) }.getOrElse {
            throw NonRetryableJobException("Cohort ${payload.cohortId} has unknown system '${cohort.system}'")
        }
        val adapter = adapters.find { it.system == system }
            ?: throw NonRetryableJobException("No CohortAdapter registered for system $system")

        when (payload.intent) {
            SyncCohortMembershipIntent.ADD -> add(payload, cohort.id!!, cohort.label, cohort.system, adapter)
            SyncCohortMembershipIntent.REMOVE -> remove(payload, cohort.id!!, cohort.system, adapter)
        }
    }

    private fun add(
        payload: CohortJobs.SyncCohortMembershipPayload,
        cohortId: Long,
        cohortLabel: String,
        system: String,
        adapter: CohortAdapter,
    ) {
        val externalUserId = externalIds.find(USER_AGGREGATE, payload.userId, system)?.externalId
        if (externalUserId == null) {
            jobs.enqueue(ContactJobs.SyncContact, ContactJobs.SyncContactPayload(payload.userId))
            throw CohortMembershipNotReadyException(
                "user ${payload.userId} has no $system external id — enqueued SyncContact, will retry",
            )
        }
        val externalCohortId = externalIds.find(COHORT_AGGREGATE, cohortId, system)?.externalId
            ?: createExternal(cohortId, cohortLabel, system, adapter)
        adapter.addMember(externalUserId, externalCohortId)
        log.debug("Added user {} to {} cohort {} (ext={})", payload.userId, system, cohortId, externalCohortId)
    }

    private fun remove(
        payload: CohortJobs.SyncCohortMembershipPayload,
        cohortId: Long,
        system: String,
        adapter: CohortAdapter,
    ) {
        val externalUserId = externalIds.find(USER_AGGREGATE, payload.userId, system)?.externalId
        val externalCohortId = externalIds.find(COHORT_AGGREGATE, cohortId, system)?.externalId
        if (externalUserId == null || externalCohortId == null) {
            log.debug(
                "No $system external ids for user {} / cohort {} — skipping removal",
                payload.userId, cohortId,
            )
            return
        }
        adapter.removeMember(externalUserId, externalCohortId)
        log.debug("Removed user {} from {} cohort {} (ext={})", payload.userId, system, cohortId, externalCohortId)
    }

    private fun createExternal(
        cohortId: Long,
        cohortLabel: String,
        system: String,
        adapter: CohortAdapter,
    ): String {
        log.info("Lazy-creating $system cohort {} ('{}') externally", cohortId, cohortLabel)
        val externalId = adapter.createCohort(cohortLabel)
        externalIds.upsert(COHORT_AGGREGATE, cohortId, system, externalId)
        return externalId
    }

    companion object {
        private const val USER_AGGREGATE = "USER"
        private const val COHORT_AGGREGATE = "COHORT"
        private val log = LoggerFactory.getLogger(SyncCohortMembershipJob::class.java)
    }
}

/**
 * Thrown when the (user, cohort) pair cannot be pushed yet because a
 * prerequisite (typically the user's external contact id) has not been
 * materialised. The thrown exception is *not* annotated as
 * non-retryable, so the job framework re-runs it after backoff once
 * the prerequisite job has had a chance to complete.
 */
internal class CohortMembershipNotReadyException(message: String) : RuntimeException(message)
