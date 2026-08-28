package net.blueshell.api.cohort.domain

import net.blueshell.api.jobs.api.AbstractJsonJobHandler
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Driving adapter: invokes
 * [CohortReconciliation.reconcileAllContributionPeriodCohorts]. No
 * payload; the use case walks all periods on its own.
 */
@Component
class ReconcileAllContributionPeriodCohortsJobHandler(
    objectMapper: ObjectMapper,
    private val cohortReconciliation: CohortReconciliation,
) : AbstractJsonJobHandler<CohortJobs.ReconcileAllContributionPeriodCohortsPayload>(
    objectMapper,
    CohortJobs.ReconcileAllContributionPeriodCohorts.payloadType,
) {
    override val jobType: String = CohortJobs.ReconcileAllContributionPeriodCohorts.type

    override fun handlePayload(payload: CohortJobs.ReconcileAllContributionPeriodCohortsPayload) {
        cohortReconciliation.reconcileAllContributionPeriodCohorts()
    }
}
