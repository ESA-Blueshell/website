package net.blueshell.api.platform.integration.cohort.adapter.job

import net.blueshell.api.platform.integration.cohort.application.InboundReconcile
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.shared.job.CohortJobs
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class ApplyInboundReconcileJobHandler(
    objectMapper: ObjectMapper,
    private val inbound: InboundReconcile,
) : AbstractJsonJobHandler<CohortJobs.ApplyInboundReconcilePayload>(
    objectMapper,
    CohortJobs.ApplyInboundReconcile.payloadType,
) {
    override val jobType: String = CohortJobs.ApplyInboundReconcile.type

    override fun handlePayload(payload: CohortJobs.ApplyInboundReconcilePayload) {
        inbound.applyJob(payload)
    }
}
