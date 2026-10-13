package net.blueshell.api.event.domain

import net.blueshell.api.email.api.EmailJob
import net.blueshell.api.email.api.EmailSenderService
import net.blueshell.api.shared.email.EmailContent
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class EventSignUpRemovedEmailJob(
    objectMapper: ObjectMapper,
    private val emails: EmailSenderService,
    @param:Value($$"${frontend.url}") private val frontendUrl: String,
) : EmailJob<EventJobs.EventSignUpRemovedPayload>(
        objectMapper,
        EventJobs.EventSignUpRemoved,
        emails,
    ) {
    override fun compose(payload: EventJobs.EventSignUpRemovedPayload): EmailContent = createEventSignUpRemovedEmail(payload, frontendUrl)
}
