package net.blueshell.api.platform.integration.cohort.adapter.job

import net.blueshell.api.platform.integration.cohort.port.`in`.CohortTargeting
import net.blueshell.api.jobs.api.AbstractJsonJobHandler
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.platform.integration.cohort.application.CohortJobs
import net.blueshell.api.shared.job.NonRetryableJobException
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
        val system = runCatching { TargetSystem.valueOf(payload.system) }.getOrElse {
            throw NonRetryableJobException("Delete-target job has unknown system '${payload.system}'")
        }
        targeting.deleteTarget(system, payload.externalTargetId)
    }
}
