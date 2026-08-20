package net.blueshell.api.platform.integration.email.application.job

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.platform.integration.email.application.service.EmailSenderService
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.shared.job.EmailJobs
import org.springframework.stereotype.Component

@Component
class RecoveryEmailJob(
    objectMapper: ObjectMapper,
    private val emails: EmailSenderService
) : AbstractJsonJobHandler<EmailJobs.RecoveryPayload>(
    objectMapper,
    EmailJobs.Recovery.payloadType,
) {
    override val jobType: String = EmailJobs.Recovery.type

    override fun handlePayload(payload: EmailJobs.RecoveryPayload) {
        emails.sendUserResetEmail(payload.userId, payload.token, payload.tokenPurpose, currentExecutionId)
    }
}
