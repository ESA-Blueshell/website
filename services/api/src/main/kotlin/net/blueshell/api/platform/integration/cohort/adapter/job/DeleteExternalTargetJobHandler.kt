package net.blueshell.api.platform.integration.cohort.adapter.job

import net.blueshell.api.platform.integration.cohort.port.`in`.CohortTargeting
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.platform.integration.sync.port.TargetSystem
import net.blueshell.api.shared.job.CohortJobs
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Driving adapter: invokes [CohortTargeting.deleteTarget]. Removes one
 * external target after a switch dropped its mapping. Provider "already
 * gone" is idempotent success.
 */
@Component
class DeleteExternalTargetJobHandler(
    objectMapper: ObjectMapper,
    private val targeting: CohortTargeting,
) : AbstractJsonJobHandler<CohortJobs.DeleteExternalTargetPayload>(
    objectMapper,
    CohortJobs.DeleteExternalTarget.payloadType,
) {
    override val jobType: String = CohortJobs.DeleteExternalTarget.type

    override fun handlePayload(payload: CohortJobs.DeleteExternalTargetPayload) {
        targeting.deleteTarget(TargetSystem.valueOf(payload.system), payload.externalTargetId)
    }
}
