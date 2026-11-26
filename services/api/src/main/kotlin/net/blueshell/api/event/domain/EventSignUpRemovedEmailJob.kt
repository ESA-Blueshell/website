package net.blueshell.api.event.domain

import net.blueshell.api.email.api.EmailSenderService
import net.blueshell.api.jobs.api.AbstractJsonJobHandler
import net.blueshell.api.jobs.api.EMAIL_RETRY_SCHEDULE
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class EventSignUpRemovedEmailJob(
    objectMapper: ObjectMapper,
    private val emails: EmailSenderService,
    @param:Value($$"${frontend.url}") private val frontendUrl: String,
) : AbstractJsonJobHandler<EventJobs.EventSignUpRemovedPayload>(
        objectMapper,
        EventJobs.EventSignUpRemoved,
    ) {
    override val retrySchedule get() = EMAIL_RETRY_SCHEDULE

    override fun handlePayload(payload: EventJobs.EventSignUpRemovedPayload) {
        emails.send(
            createEventSignUpRemovedEmail(payload, frontendUrl),
            EventJobs.EventSignUpRemoved.type,
            currentExecutionId,
        )
    }
}
