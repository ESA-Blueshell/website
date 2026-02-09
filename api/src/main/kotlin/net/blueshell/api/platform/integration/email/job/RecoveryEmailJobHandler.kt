package net.blueshell.api.platform.integration.email.job

import com.fasterxml.jackson.databind.ObjectMapper
import net.blueshell.api.platform.integration.email.service.EmailService
import net.blueshell.api.platform.integration.queue.AbstractMailJobHandler
import org.springframework.stereotype.Component

@Component
class RecoveryEmailJobHandler(
    objectMapper: ObjectMapper,
    emails: EmailService
) : AbstractMailJobHandler<RecoveryEmailPayload>(objectMapper, RecoveryEmailPayload::class.java, emails) {
    override val jobType: String = JOB_TYPE

    override fun handlePayload(payload: RecoveryEmailPayload) {
        emails.sendUserResetEmail(payload.userId, payload.token, payload.resetType)
    }

    companion object {
        const val JOB_TYPE = "email.recovery"
    }
}
