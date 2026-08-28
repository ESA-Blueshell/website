package net.blueshell.api.domain.event.application.job

import net.blueshell.api.domain.event.application.EventSignUpService
import net.blueshell.api.domain.event.application.email.createEventSignupEmail
import net.blueshell.api.email.api.EmailSenderService
import net.blueshell.api.jobs.api.AbstractJsonJobHandler
import net.blueshell.api.shared.job.EmailJobs
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
) : AbstractJsonJobHandler<EmailJobs.EventSignupPayload>(
    objectMapper,
    EmailJobs.EventSignup.payloadType,
) {
    override val jobType: String = EmailJobs.EventSignup.type

    override fun handlePayload(payload: EmailJobs.EventSignupPayload) {
        val eventSignUp = requireExists { eventSignUps.findById(payload.eventSignUpId) }
        emails.send(
            createEventSignupEmail(eventSignUp, frontendUrl, payload.guestAccessToken),
            "email.event-signup",
            currentExecutionId,
        )
    }
}
