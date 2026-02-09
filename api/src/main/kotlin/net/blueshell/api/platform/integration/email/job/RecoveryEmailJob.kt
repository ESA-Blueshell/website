package net.blueshell.api.platform.integration.email.job

import com.fasterxml.jackson.databind.ObjectMapper
import net.blueshell.api.platform.integration.email.service.EmailService
import net.blueshell.api.platform.integration.queue.AbstractMailJobHandler
import net.blueshell.api.shared.enums.ResetType
import org.springframework.stereotype.Component

@Component
class RecoveryEmailJob(
    objectMapper: ObjectMapper,
    emails: EmailService
) : AbstractMailJobHandler<RecoveryEmailJob.Payload>(objectMapper, Payload::class.java, emails) {
    override val jobType: String = TYPE

    override fun handlePayload(payload: Payload) {
        emails.sendUserResetEmail(payload.userId, payload.token, payload.resetType)
    }

    companion object {
        const val TYPE = "email.recovery"
    }

    data class Payload(
        val userId: Long,
        val token: String,
        val resetType: ResetType
    )
}
