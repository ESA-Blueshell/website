package net.blueshell.api.platform.integration.email.job

import com.fasterxml.jackson.databind.ObjectMapper
import net.blueshell.api.platform.integration.email.service.EmailService
import net.blueshell.api.platform.integration.queue.AbstractMailJobHandler
import net.blueshell.api.shared.job.EmailJobs
import org.springframework.stereotype.Component

@Component
class EventSignupEmailJob(
    objectMapper: ObjectMapper,
    emails: EmailService
) : AbstractMailJobHandler<EmailJobs.EventSignupPayload>(
    objectMapper,
    EmailJobs.EventSignup.payloadType,
    emails
) {
    override val jobType: String = EmailJobs.EventSignup.type

    override fun handlePayload(payload: EmailJobs.EventSignupPayload) {
        emails.sendEventSignupEmail(payload.eventSignUpId)
    }
}
