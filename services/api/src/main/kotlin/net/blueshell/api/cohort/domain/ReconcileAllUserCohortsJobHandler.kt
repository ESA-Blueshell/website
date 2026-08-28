package net.blueshell.api.cohort.domain

import net.blueshell.api.jobs.api.AbstractJsonJobHandler
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Driving adapter: invokes [CohortReconciliation.reconcileAllUserCohorts].
 * Spawn job — the use case enqueues one `cohort.evaluate-user` per user.
 */
@Component
class ReconcileAllUserCohortsJobHandler(
    objectMapper: ObjectMapper,
    private val cohortReconciliation: CohortReconciliation,
) : AbstractJsonJobHandler<CohortJobs.ReconcileAllUserCohortsPayload>(
    objectMapper,
    CohortJobs.ReconcileAllUserCohorts.payloadType,
) {
    override val jobType: String = CohortJobs.ReconcileAllUserCohorts.type

    override fun handlePayload(payload: CohortJobs.ReconcileAllUserCohortsPayload) {
        cohortReconciliation.reconcileAllUserCohorts()
    }
}
