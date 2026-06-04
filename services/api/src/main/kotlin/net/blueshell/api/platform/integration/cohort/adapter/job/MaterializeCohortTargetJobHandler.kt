package net.blueshell.api.platform.integration.cohort.adapter.job

import net.blueshell.api.platform.integration.cohort.port.`in`.CohortTargeting
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.shared.job.CohortJobs
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Driving adapter: invokes [CohortTargeting.materialize]. Creates one
 * cohort's external target when it has none yet, so a per-member ADD that
 * found no target id can retry once it exists. Deduplicated per cohort.
 */
@Component
class MaterializeCohortTargetJobHandler(
    objectMapper: ObjectMapper,
    private val targeting: CohortTargeting,
) : AbstractJsonJobHandler<CohortJobs.MaterializeCohortTargetPayload>(
    objectMapper,
    CohortJobs.MaterializeCohortTarget.payloadType,
) {
    override val jobType: String = CohortJobs.MaterializeCohortTarget.type

    override fun handlePayload(payload: CohortJobs.MaterializeCohortTargetPayload) {
        targeting.materialize(payload.cohortId)
    }
}
