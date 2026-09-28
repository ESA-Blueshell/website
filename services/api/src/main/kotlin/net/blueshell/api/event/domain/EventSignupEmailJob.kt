package net.blueshell.api.event.domain

import net.blueshell.api.email.api.EmailSenderService
import net.blueshell.api.jobs.api.AbstractJsonJobHandler
import net.blueshell.api.shared.job.requireExists
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class EventSignupEmailJob(
    objectMapper: ObjectMapper,
    private val eventSignUps: EventSignUpService,
    private val emails: EmailSenderService,
    @param:Value($$"${frontend.url}") private val frontendUrl: String,
) : AbstractJsonJobHandler<EventJobs.EventSignupPayload>(
        objectMapper,
        EventJobs.EventSignup,
    ) {
    override fun handlePayload(payload: EventJobs.EventSignupPayload) {
        val eventSignUp = requireExists { eventSignUps.findById(payload.eventSignUpId) }
        emails.send(
            createEventSignupEmail(eventSignUp, frontendUrl, payload.guestAccessToken),
            "email.event-signup",
            currentExecutionId,
        )
    }
}
