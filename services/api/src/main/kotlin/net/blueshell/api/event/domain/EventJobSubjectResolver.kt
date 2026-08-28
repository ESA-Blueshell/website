package net.blueshell.api.event.domain

import net.blueshell.api.jobs.web.JobSubjectResolver
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import net.blueshell.api.event.api.EventService

@Component
@Order(20)
class EventJobSubjectResolver(
    private val events: EventService,
) : JobSubjectResolver {
    override val payloadFields = listOf("eventId")
    override val entityType = "EVENT"

    override fun label(id: Long): String {
        val event = events.findByIdIncludingDeletedOrNull(id) ?: return "Event #$id"
        return "Event #$id: ${event.title}"
    }
}
