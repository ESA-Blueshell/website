package net.blueshell.api.platform.integration.cohort.adapter.job

import net.blueshell.api.platform.integration.cohort.port.`in`.CohortMembershipSync
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.shared.job.CohortJobs
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Driving (inbound) adapter that lets the job queue invoke the
 * [CohortMembershipSync] use case. Deserialises the JSON payload and
 * hands the triple off to the application port — no business logic
 * lives here.
 *
 * Lives under `cohort/adapter/job/` rather than the legacy
 * `application/job/` location: in a true hexagonal split, a job
 * handler is a driving adapter (it adapts the queue's "execute this
 * payload" message to the application's use-case interface).
 */
@Component
class SyncCohortMembershipJobHandler(
    objectMapper: ObjectMapper,
    private val cohortMembershipSync: CohortMembershipSync,
) : AbstractJsonJobHandler<CohortJobs.SyncCohortMembershipPayload>(
    objectMapper,
    CohortJobs.SyncCohortMembership.payloadType,
) {
    override val jobType: String = CohortJobs.SyncCohortMembership.type

    override fun handlePayload(payload: CohortJobs.SyncCohortMembershipPayload) {
        cohortMembershipSync.sync(payload.userId, payload.cohortId, payload.intent)
    }
}
