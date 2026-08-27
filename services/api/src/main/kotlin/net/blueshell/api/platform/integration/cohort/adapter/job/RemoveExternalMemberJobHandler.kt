package net.blueshell.api.platform.integration.cohort.adapter.job

import net.blueshell.api.platform.integration.cohort.port.`in`.CohortRemediation
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.platform.integration.cohort.application.CohortJobs
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Driving adapter: invokes [CohortRemediation.removeExternalMember].
 * Removes one external member from the cohort's external target.
 * The default dedup key (payload hash) collapses double-click races.
 */
@Component
class RemoveExternalMemberJobHandler(
    objectMapper: ObjectMapper,
    private val remediation: CohortRemediation,
) : AbstractJsonJobHandler<CohortJobs.RemoveExternalMemberPayload>(
    objectMapper,
    CohortJobs.RemoveExternalMember.payloadType,
) {
    override val jobType: String = CohortJobs.RemoveExternalMember.type

    override fun handlePayload(payload: CohortJobs.RemoveExternalMemberPayload) {
        remediation.removeExternalMember(payload.cohortId, payload.externalUserId)
    }
}
