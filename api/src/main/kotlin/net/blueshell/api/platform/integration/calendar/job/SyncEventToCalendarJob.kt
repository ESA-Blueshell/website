package net.blueshell.api.platform.integration.calendar.job

import com.fasterxml.jackson.databind.ObjectMapper
import net.blueshell.api.domain.event.application.EventService
import net.blueshell.api.domain.event.application.calendar.CalendarAdapter
import net.blueshell.api.domain.event.application.calendar.CalendarEventData
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.shared.job.CalendarEventRef
import net.blueshell.api.shared.job.CalendarJobs
import org.springframework.stereotype.Component

/**
 * Job handler for syncing events to external calendar.
 *
 * Uses CalendarAdapter (ADR-019 ACL) to isolate from specific calendar provider.
 * Handles add/update/remove logic based on event approval status.
 */
@Component
class SyncEventToCalendarJob(
    objectMapper: ObjectMapper,
    private val calendarAdapter: CalendarAdapter,
    private val events: EventService
) : AbstractJsonJobHandler<CalendarEventRef>(objectMapper, CalendarJobs.SyncEvent.payloadType) {
    override val jobType: String = CalendarJobs.SyncEvent.type

    override fun handlePayload(payload: CalendarEventRef) {
        val event = events.findById(payload.eventId)

        // Use adapter to sync event with external calendar
        val eventData = CalendarEventData(
            title = event.title,
            location = event.location,
            description = event.description,
            startTime = event.startTime,
            endTime = event.endTime,
            approved = event.approved
        )

        val ref = calendarAdapter.syncEvent(
            eventId = event.id!!,
            eventData = eventData,
            externalId = event.googleId
        )

        // Update event with external calendar ID (null if removed)
        event.googleId = ref?.externalId
        events.update(event)
    }

}
