package net.blueshell.api.platform.integration.email.job

import com.fasterxml.jackson.databind.ObjectMapper
import net.blueshell.api.platform.integration.email.service.EmailService
import net.blueshell.api.platform.integration.queue.AbstractMailJobHandler
import org.springframework.stereotype.Component

@Component
class EventSignupEmailJobHandler(
    objectMapper: ObjectMapper,
    emails: EmailService
) : AbstractMailJobHandler<EventSignupEmailPayload>(objectMapper, EventSignupEmailPayload::class.java, emails) {
    override val jobType: String = JOB_TYPE

    override fun handlePayload(payload: EventSignupEmailPayload) {
        emails.sendEventSignupEmail(payload.eventSignUpId)
    }

    companion object {
        const val JOB_TYPE = "email.event-signup"
    }
}
