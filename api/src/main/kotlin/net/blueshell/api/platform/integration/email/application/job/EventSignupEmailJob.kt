package net.blueshell.api.platform.integration.email.application.job

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.platform.integration.email.application.service.EmailSenderService
import net.blueshell.api.platform.integration.queue.AbstractMailJobHandler
import net.blueshell.api.shared.job.EmailJobs
import org.springframework.stereotype.Component

@Component
class EventSignupEmailJob(
    objectMapper: ObjectMapper,
    emails: EmailSenderService
) : AbstractMailJobHandler<EmailJobs.EventSignupPayload>(
    objectMapper,
    EmailJobs.EventSignup.payloadType,
    emails
) {
    override val jobType: String = EmailJobs.EventSignup.type

    override fun handlePayload(payload: EmailJobs.EventSignupPayload) {
        emails.sendEventSignupEmail(payload.eventSignUpId, payload.guestAccessToken, currentExecutionId)
    }
}
