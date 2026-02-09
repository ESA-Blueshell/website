package net.blueshell.api.platform.integration.email.job

import com.fasterxml.jackson.databind.ObjectMapper
import net.blueshell.api.platform.integration.email.service.EmailService
import net.blueshell.api.platform.integration.queue.AbstractMailJobHandler
import org.springframework.stereotype.Component

@Component
class EventSignupEmailJob(
    objectMapper: ObjectMapper,
    emails: EmailService
) : AbstractMailJobHandler<EventSignupEmailJob.Payload>(objectMapper, Payload::class.java, emails) {
    override val jobType: String = TYPE

    override fun handlePayload(payload: Payload) {
        emails.sendEventSignupEmail(payload.eventSignUpId)
    }

    companion object {
        const val TYPE = "email.event-signup"
    }

    data class Payload(
        val eventSignUpId: Long
    )
}
