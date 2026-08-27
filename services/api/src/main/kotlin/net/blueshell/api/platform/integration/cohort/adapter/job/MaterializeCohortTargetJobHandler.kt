package net.blueshell.api.platform.integration.cohort.adapter.job

import net.blueshell.api.platform.integration.cohort.port.`in`.CohortTargeting
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.platform.integration.cohort.application.CohortJobs
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Driving adapter for stale `cohort.materialize-target` rows. The use case
 * now returns an existing target id or fails terminally; it never creates a
 * provider target.
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
