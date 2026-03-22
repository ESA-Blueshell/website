package net.blueshell.api.domain.event.application.calendar

import java.time.Instant

/**
 * Domain interface for calendar integration (ADR-019: Anti-Corruption Layer)
 *
 * This interface defines domain-friendly calendar operations without exposing
 * external calendar API details (e.g., Google Calendar).
 *
 * Platform layer provides concrete implementations (GoogleCalendarAdapter).
 */
interface CalendarAdapter {
    /**
     * Add an event to the external calendar.
     *
     * @param eventId The domain event ID (for tracking)
     * @param eventData The event data to publish
     * @return Reference to the external calendar event
     * @throws CalendarServiceException if the operation fails
     */
    fun addEvent(eventId: Long, eventData: CalendarEventData): CalendarEventRef

    /**
     * Update an existing event in the external calendar.
     *
     * @param eventId The domain event ID
     * @param externalId The external calendar event ID
     * @param eventData The updated event data
     * @throws CalendarServiceException if the operation fails
     */
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
     * Synchronize an event with the external calendar.
     * - If externalId is null and event is approved: adds the event
     * - If externalId exists and event is approved: updates the event
     * - If externalId exists and event is not approved: removes the event
     *
     * @param eventId The domain event ID
     * @param eventData The event data
     * @param externalId The external calendar event ID (if already published)
     * @return Updated external event reference (null if removed)
     * @throws CalendarServiceException if the operation fails
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
