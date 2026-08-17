package net.blueshell.api.platform.integration.email.application.job

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.platform.integration.email.application.service.EmailSenderService
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.shared.job.EmailJobs
import org.springframework.stereotype.Component

@Component
class IncassoNotificationEmailJob(
    objectMapper: ObjectMapper,
    private val emails: EmailSenderService
) : AbstractJsonJobHandler<EmailJobs.IncassoNotificationPayload>(
    objectMapper,
    EmailJobs.IncassoNotification.payloadType,
) {
    override val jobType: String = EmailJobs.IncassoNotification.type

    override fun handlePayload(payload: EmailJobs.IncassoNotificationPayload) {
        emails.sendIncassoNotificationEmail(payload.userId, payload.contributionPeriodId, currentExecutionId)
    }
}
