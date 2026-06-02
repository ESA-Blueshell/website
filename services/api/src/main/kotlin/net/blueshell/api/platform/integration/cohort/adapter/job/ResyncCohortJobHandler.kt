package net.blueshell.api.platform.integration.cohort.adapter.job

import net.blueshell.api.platform.integration.cohort.port.`in`.CohortReconciliation
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.shared.job.CohortJobs
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Driving adapter: invokes [CohortReconciliation.resyncCohort]. The
 * use case re-pushes every active member of the given cohort back
 * to its external system without changing local state.
 */
@Component
class ResyncCohortJobHandler(
    objectMapper: ObjectMapper,
    private val cohortReconciliation: CohortReconciliation,
) : AbstractJsonJobHandler<CohortJobs.ResyncCohortPayload>(
    objectMapper,
    CohortJobs.ResyncCohort.payloadType,
) {
    override val jobType: String = CohortJobs.ResyncCohort.type

    override fun handlePayload(payload: CohortJobs.ResyncCohortPayload) {
        cohortReconciliation.resyncCohort(payload.cohortId)
    }
}
