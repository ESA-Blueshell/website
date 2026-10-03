package net.blueshell.api.contribution.domain

import net.blueshell.api.jobs.api.AbstractJsonJobHandler
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/** The daily wipe of bank details that outlived their mandate's last collection by 13 months. */
@Component
class WipeBankDetailsJob(
    objectMapper: ObjectMapper,
    private val retention: BankDetailsRetention,
) : AbstractJsonJobHandler<ContributionJobs.WipeBankDetailsPayload>(objectMapper, ContributionJobs.WipeBankDetails) {
    override fun handlePayload(payload: ContributionJobs.WipeBankDetailsPayload) {
        if (retention.wipeExpired() == 0) skip("No bank details are due to be wiped")
    }
}
