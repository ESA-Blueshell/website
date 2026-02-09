package net.blueshell.api.platform.integration.email.job

import com.fasterxml.jackson.databind.ObjectMapper
import net.blueshell.api.platform.integration.email.service.EmailService
import net.blueshell.api.platform.integration.queue.AbstractMailJobHandler
import net.blueshell.api.platform.integration.queue.EmailJobs
import org.springframework.stereotype.Component

@Component
class RecoveryEmailJob(
    objectMapper: ObjectMapper,
    emails: EmailService
) : AbstractMailJobHandler<EmailJobs.RecoveryPayload>(
    objectMapper,
    EmailJobs.Recovery.payloadType,
    emails
) {
    override val jobType: String = EmailJobs.Recovery.type

    override fun handlePayload(payload: EmailJobs.RecoveryPayload) {
        emails.sendUserResetEmail(payload.userId, payload.token, payload.resetType)
    }
}
