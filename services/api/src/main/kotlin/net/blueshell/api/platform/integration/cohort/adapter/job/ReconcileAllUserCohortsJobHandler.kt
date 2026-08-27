package net.blueshell.api.platform.integration.cohort.adapter.job

import net.blueshell.api.platform.integration.cohort.port.`in`.CohortReconciliation
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.platform.integration.cohort.application.CohortJobs
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
