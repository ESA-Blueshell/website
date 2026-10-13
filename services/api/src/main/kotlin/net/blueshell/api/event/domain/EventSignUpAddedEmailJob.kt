package net.blueshell.api.event.domain

import net.blueshell.api.email.api.EmailJob
import net.blueshell.api.email.api.EmailSenderService
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.shared.job.requireExists
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class EventSignUpAddedEmailJob(
    objectMapper: ObjectMapper,
    private val eventSignUps: EventSignUpService,
    private val emails: EmailSenderService,
    @param:Value($$"${frontend.url}") private val frontendUrl: String,
) : EmailJob<EventJobs.EventSignupPayload>(
        objectMapper,
        EventJobs.EventSignUpAdded,
        emails,
    ) {
    override fun compose(payload: EventJobs.EventSignupPayload): EmailContent {
        val eventSignUp = requireExists { eventSignUps.findById(payload.eventSignUpId) }
        return createEventSignUpAddedEmail(eventSignUp, frontendUrl, payload.guestAccessToken)
    }
}
