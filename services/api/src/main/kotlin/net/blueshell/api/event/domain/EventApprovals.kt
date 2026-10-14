package net.blueshell.api.event.domain

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.event.persistence.ApprovedEvent
import net.blueshell.api.event.persistence.ApprovedEventRepository
import net.blueshell.api.event.persistence.Event
import net.blueshell.api.event.persistence.EventRepository
import net.blueshell.api.survey.persistence.Survey
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import java.time.Clock

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
    PINGED_ROLES,
    GAMES,
    BANNER,
    SIGN_UP_FORM,
}

/** An event waiting for the board, and what changed since it was last approved, if it ever was. */
data class QueuedEvent(
    val event: Event,
    val reapproval: Boolean,
    val changes: List<EventField>,
)

/**
 * Keeps each event as the board last approved it, and says which of its fields changed since. An
 * event the board ever approved is a re-approval, whether a committee's edit or the board's own
 * unapproving sent it back; one approved before this was kept names no field.
 */
@Service
class EventApprovals(
    private val approved: ApprovedEventRepository,
    private val events: EventRepository,
    private val mapper: ObjectMapper,
    private val clock: Clock,
) {
    // One statement, so the newest approval replaces the one before and two at once cannot collide.
    @Transactional
    fun record(event: Event) {
        approved.keep(requireNotNull(event.id), mapper.writeValueAsString(valuesOf(event).mapKeys { it.key.name }))
    }

    /** Every event still to come that waits for the board, soonest first. */
    @Transactional(readOnly = true)
    fun queue(): List<QueuedEvent> {
        val waiting = events.findByApprovedFalseAndEndTimeGreaterThanEqualOrderByStartTimeAsc(clock.instant())
        val kept = approved.findAllById(waiting.mapNotNull { it.id }).associateBy { it.eventId }
        return waiting.map { event ->
            val before = kept[event.id] ?: return@map QueuedEvent(event, false, emptyList())
            QueuedEvent(event, true, changedSince(event, before))
        }
    }

    private fun changedSince(
        event: Event,
        before: ApprovedEvent,
    ): List<EventField> {
        val was = mapper.readValue(before.fields, object : TypeReference<Map<String, String?>>() {})
        return valuesOf(event).filter { (field, now) -> was[field.name] != now }.keys.toList()
    }

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
            EventField.PINGED_ROLES to sortedList(event.pingedRoles.map { it.roleId }),
            EventField.GAMES to sortedList(event.gameCodes),
            EventField.BANNER to event.banner?.fileId?.toString(),
            EventField.SIGN_UP_FORM to event.signUpForm?.let(::formOf),
        )

    // What a person answering sees: each question's kind, wording, choices and whether it must be answered.
    private fun formOf(form: Survey): String =
        form.questions
            .sortedBy { it.idx }
            .joinToString("|") { "${it.type}:${it.label}:${it.choiceLabels.orEmpty().joinToString(";")}:${it.required}" }
}

private fun sortedList(values: Collection<String>) = values.sorted().joinToString(",")
