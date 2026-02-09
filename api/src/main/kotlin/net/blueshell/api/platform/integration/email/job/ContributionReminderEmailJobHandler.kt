package net.blueshell.api.platform.integration.email.job

import com.fasterxml.jackson.databind.ObjectMapper
import net.blueshell.api.platform.integration.email.service.EmailService
import net.blueshell.api.platform.integration.queue.AbstractMailJobHandler
import org.springframework.stereotype.Component

@Component
class ContributionReminderEmailJobHandler(
    objectMapper: ObjectMapper,
    emails: EmailService
) : AbstractMailJobHandler<ContributionReminderEmailPayload>(
    objectMapper,
    ContributionReminderEmailPayload::class.java,
    emails
) {
    override val jobType: String = JOB_TYPE

    override fun handlePayload(payload: ContributionReminderEmailPayload) {
        emails.sendContributionReminderEmail(payload.userId, payload.contributionPeriodId)
    }

    companion object {
        const val JOB_TYPE = "email.contribution-reminder"
    }
}
