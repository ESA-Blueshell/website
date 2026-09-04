package net.blueshell.api.event.api

import java.time.Instant

/**
 * Calendar operations in the domain's own terms, with no vendor detail crossing the boundary
 * (ADR-019). The platform layer implements it.
 */
interface CalendarAdapter {
    /** Publishes the event to the external calendar, answering with its reference there. */
    fun addEvent(eventId: Long, eventData: CalendarEventData): CalendarEventRef

    /** Brings an already-published event in the external calendar up to date. */
    fun updateEvent(eventId: Long, externalId: String, eventData: CalendarEventData)

    /**
     * Remove an event from the external calendar.
     *
     * @param eventId The domain event ID
     * @param externalId The external calendar event ID
     * @throws CalendarServiceException if the operation fails
     */
    fun removeEvent(eventId: Long, externalId: String)

    /**
     * Brings the external calendar in line with the event: adds it where it is approved and
     * unpublished, updates it where it is approved and published, removes it where approval has
     * gone. Answers null once removed.
     */
    fun syncEvent(eventId: Long, eventData: CalendarEventData, externalId: String?): CalendarEventRef?
}

/**
 * Domain data for calendar events.
 * Contains only the information needed for calendar publishing,
 * isolated from domain entity structure.
 */
data class CalendarEventData(
    val title: String,
    val location: String?,
    val description: String?,
    val startTime: Instant,
    val endTime: Instant,
    val approved: Boolean
)

/**
 * Reference to an external calendar event.
 * Returned by calendar operations to track external IDs.
 */
data class CalendarEventRef(
    val externalId: String,
    val externalUrl: String?
)

/**
 * Domain exception for calendar operations.
 * Thrown when calendar adapter operations fail.
 */
class CalendarServiceException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
