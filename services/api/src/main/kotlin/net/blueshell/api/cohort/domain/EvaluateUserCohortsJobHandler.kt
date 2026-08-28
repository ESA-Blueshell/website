package net.blueshell.api.cohort.domain

import net.blueshell.api.jobs.api.AbstractJsonJobHandler
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Driving adapter: invokes the [CohortReconciliation.evaluateUserCohorts]
 * use case for one user. Forwards the userId from the queue payload to
 * the inbound port; no business logic here.
 */
@Component
class EvaluateUserCohortsJobHandler(
    objectMapper: ObjectMapper,
    private val cohortReconciliation: CohortReconciliation,
) : AbstractJsonJobHandler<CohortJobs.EvaluateUserCohortsPayload>(
    objectMapper,
    CohortJobs.EvaluateUserCohorts.payloadType,
) {
    override val jobType: String = CohortJobs.EvaluateUserCohorts.type

    override fun handlePayload(payload: CohortJobs.EvaluateUserCohortsPayload) {
        cohortReconciliation.evaluateUserCohorts(payload.userId)
    }
}
