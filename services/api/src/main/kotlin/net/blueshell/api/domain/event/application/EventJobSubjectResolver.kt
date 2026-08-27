package net.blueshell.api.domain.event.application

import net.blueshell.api.platform.integration.job.web.port.JobSubjectResolver
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

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
