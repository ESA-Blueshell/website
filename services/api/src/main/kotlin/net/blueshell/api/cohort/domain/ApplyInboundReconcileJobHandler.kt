package net.blueshell.api.cohort.domain

import net.blueshell.api.jobs.api.AbstractJsonJobHandler
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
