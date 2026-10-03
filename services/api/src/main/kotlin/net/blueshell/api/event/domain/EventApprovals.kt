package net.blueshell.api.event.domain

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.event.persistence.ApprovedEvent
import net.blueshell.api.event.persistence.ApprovedEventRepository
import net.blueshell.api.event.persistence.Event
import org.springframework.stereotype.Service
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper

/** What of an event a re-approval can have changed, as the queue names it. */
@Schema(name = "EventField", enumAsRef = true)
enum class EventField {
    TITLE,
    DESCRIPTION,
    LOCATION,
    TIMES,
    PRICES,
    MEMBERS_ONLY,
    SIGN_UP,
    COMMITTEE,
}

/** An event waiting for the board, and what changed since it was last approved, if it ever was. */
data class QueuedEvent(
    val event: Event,
    val reapproval: Boolean,
    val changes: List<EventField>,
)

/**
 * Keeps each event as the board last approved it, and says which of its fields changed since. An
 * event approved before this was kept has no record, so its re-approval names no field.
 */
@Service
class EventApprovals(
    private val approved: ApprovedEventRepository,
    private val mapper: ObjectMapper,
) {
    fun record(event: Event) {
        val id = requireNotNull(event.id)
        val fields = mapper.writeValueAsString(valuesOf(event).mapKeys { it.key.name })
        val row = approved.findById(id).orElse(null)
        if (row == null) approved.save(ApprovedEvent(id, fields)) else row.fields = fields
    }

    fun changesOf(event: Event): List<EventField> {
        val row = approved.findById(requireNotNull(event.id)).orElse(null) ?: return emptyList()
        val before = mapper.readValue(row.fields, object : TypeReference<Map<String, String?>>() {})
        return valuesOf(event).filter { (field, now) -> before[field.name] != now }.keys.toList()
    }

    fun queued(events: List<Event>): List<QueuedEvent> =
        events.map { QueuedEvent(it, it.awaitingReapproval, if (it.awaitingReapproval) changesOf(it) else emptyList()) }

    private fun valuesOf(event: Event): Map<EventField, String?> =
        mapOf(
            EventField.TITLE to event.title,
            EventField.DESCRIPTION to event.description,
            EventField.LOCATION to event.location,
            EventField.TIMES to "${event.startTime}/${event.endTime}",
            EventField.PRICES to "${event.memberPrice}/${event.publicPrice}",
            EventField.MEMBERS_ONLY to event.membersOnly.toString(),
            EventField.SIGN_UP to "${event.signUp}/${event.signUpDeadline}/${event.signUpLimit}",
            EventField.COMMITTEE to event.committeeId?.toString(),
        )
}
