package net.blueshell.api.platform.integration.calendar.adapter

import net.blueshell.api.domain.event.application.calendar.CalendarAdapter
import net.blueshell.api.domain.event.application.calendar.CalendarEventData
import net.blueshell.api.domain.event.application.calendar.CalendarEventRef
import net.blueshell.api.domain.event.application.calendar.CalendarServiceException
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.io.IOException

/**
 * Google Calendar Anti-Corruption Layer (ADR-019)
 *
 * This adapter translates between domain concepts and Google Calendar API.
 * It protects the domain from:
 * - Google API structure and breaking changes
 * - Google-specific error handling
 * - Google authentication details
 * - Google data model
 *
 * Benefits:
 * - Domain is unaware of Google Calendar specifics
 * - Easy to replace with Microsoft Calendar, Apple Calendar, etc.
 * - All Google API errors are translated to domain exceptions
 * - Testing is simplified (mock the adapter interface)
 *
 * Active in production profile only (test/dev use MockCalendarAdapter)
 */
@Service
@Primary
@Profile("!test & !dev")
class GoogleCalendarAdapter(
    private val googleCalendarClient: GoogleCalendarClient
) : CalendarAdapter {

    override fun addEvent(eventId: Long, eventData: CalendarEventData): CalendarEventRef {
        log.info("Adding event {} to Google Calendar: {}", eventId, eventData.title)

        return try {
            val result = googleCalendarClient.addEvent(
                title = eventData.title,
                location = eventData.location,
                description = eventData.description,
                startTime = eventData.startTime,
                endTime = eventData.endTime
            )

            CalendarEventRef(
                externalId = result.eventId,
                externalUrl = result.htmlLink
            )
        } catch (e: IOException) {
            log.error("Failed to add event {} to Google Calendar", eventId, e)
            throw CalendarServiceException("Failed to add event to calendar", e)
        }
    }

    override fun updateEvent(eventId: Long, externalId: String, eventData: CalendarEventData) {
        log.info("Updating event {} (googleId={}) in Google Calendar", eventId, externalId)

        try {
            googleCalendarClient.updateEvent(
                googleEventId = externalId,
                title = eventData.title,
                location = eventData.location,
                description = eventData.description,
                startTime = eventData.startTime,
                endTime = eventData.endTime
            )
        } catch (e: IOException) {
            log.error("Failed to update event {} (googleId={}) in Google Calendar", eventId, externalId, e)
            throw CalendarServiceException("Failed to update event in calendar", e)
        }
    }

    override fun removeEvent(eventId: Long, externalId: String) {
        log.info("Removing event {} (googleId={}) from Google Calendar", eventId, externalId)

        try {
            googleCalendarClient.removeEvent(externalId)
        } catch (e: IOException) {
            log.error("Failed to remove event {} (googleId={}) from Google Calendar", eventId, externalId, e)
            throw CalendarServiceException("Failed to remove event from calendar", e)
        }
    }

    override fun syncEvent(eventId: Long, eventData: CalendarEventData, externalId: String?): CalendarEventRef? {
        log.info("Syncing event {} (googleId={}) with Google Calendar", eventId, externalId)

        return when {
            // Event has external ID and is approved -> update
            externalId != null && eventData.approved -> {
                updateEvent(eventId, externalId, eventData)
                CalendarEventRef(externalId, null)
            }

            // Event has external ID but not approved -> remove
            externalId != null && !eventData.approved -> {
                removeEvent(eventId, externalId)
                null
            }

            // Event has no external ID and is approved -> add
            externalId == null && eventData.approved -> {
                addEvent(eventId, eventData)
            }

            // Event has no external ID and not approved -> nothing to do
            else -> {
                log.debug("Event {} has no external ID and is not approved, skipping sync", eventId)
                null
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(GoogleCalendarAdapter::class.java)
    }
}
