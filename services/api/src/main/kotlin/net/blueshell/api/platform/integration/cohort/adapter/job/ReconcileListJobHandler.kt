package net.blueshell.api.platform.integration.cohort.adapter.job

import net.blueshell.api.platform.integration.cohort.port.`in`.CohortRemediation
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.shared.job.CohortJobs
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Driving adapter: invokes [CohortRemediation.verifyCohort].
 * Fetches the full external member list for one cohort mapping,
 * reconciles the ledger against it, and fans out follow-up jobs for
 * discrepancies. One external API call per job execution. The job type
 * stays `cohort.reconcile-list` for queue compatibility.
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
        remediation.verifyCohort(payload.cohortId)
    }
}
