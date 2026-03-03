package net.blueshell.api.platform.integration.email.job

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.platform.integration.email.service.EmailService
import net.blueshell.api.platform.integration.queue.AbstractMailJobHandler
import net.blueshell.api.shared.job.EmailJobs
import org.springframework.stereotype.Component

@Component
class ContributionReminderEmailJob(
    objectMapper: ObjectMapper,
    emails: EmailService
) : AbstractMailJobHandler<EmailJobs.ContributionReminderPayload>(
    objectMapper,
    EmailJobs.ContributionReminder.payloadType,
    emails
) {
    override val jobType: String = EmailJobs.ContributionReminder.type

    override fun handlePayload(payload: EmailJobs.ContributionReminderPayload) {
        emails.sendContributionReminderEmail(payload.userId, payload.contributionPeriodId)
    }
}
