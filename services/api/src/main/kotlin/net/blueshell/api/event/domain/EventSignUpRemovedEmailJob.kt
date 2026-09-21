package net.blueshell.api.event.domain

import net.blueshell.api.email.api.EmailSenderService
import net.blueshell.api.jobs.api.AbstractJsonJobHandler
import net.blueshell.api.shared.job.EmailJobs
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class EventSignUpRemovedEmailJob(
    objectMapper: ObjectMapper,
    private val emails: EmailSenderService,
    @param:Value($$"${frontend.url}") private val frontendUrl: String,
) : AbstractJsonJobHandler<EmailJobs.EventSignUpRemovedPayload>(
        objectMapper,
        EmailJobs.EventSignUpRemoved.payloadType,
    ) {
    override val jobType: String = EmailJobs.EventSignUpRemoved.type

    override fun handlePayload(payload: EmailJobs.EventSignUpRemovedPayload) {
        emails.send(
            createEventSignUpRemovedEmail(payload, frontendUrl),
            EmailJobs.EventSignUpRemoved.type,
            currentExecutionId,
        )
    }
}
