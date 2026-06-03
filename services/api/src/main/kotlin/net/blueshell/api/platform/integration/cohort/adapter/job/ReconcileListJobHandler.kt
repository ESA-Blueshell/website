package net.blueshell.api.platform.integration.cohort.adapter.job

import net.blueshell.api.platform.integration.cohort.port.`in`.CohortRemediation
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.shared.job.CohortJobs
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Driving adapter: invokes [CohortRemediation.reconcileList].
 * Fetches the full external member list for one cohort mapping,
 * updates the membership ledger, and fans out follow-up jobs for
 * missing desired members. One external API call per job execution.
 */
@Component
class ReconcileListJobHandler(
    objectMapper: ObjectMapper,
    private val remediation: CohortRemediation,
) : AbstractJsonJobHandler<CohortJobs.ReconcileListPayload>(
    objectMapper,
    CohortJobs.ReconcileList.payloadType,
) {
    override val jobType: String = CohortJobs.ReconcileList.type

    override fun handlePayload(payload: CohortJobs.ReconcileListPayload) {
        remediation.reconcileList(payload.cohortId)
    }
}
