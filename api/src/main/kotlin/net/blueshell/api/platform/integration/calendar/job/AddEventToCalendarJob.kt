package net.blueshell.api.platform.integration.calendar.job

import com.fasterxml.jackson.databind.ObjectMapper
import net.blueshell.api.domain.event.application.EventService
import net.blueshell.api.domain.event.application.calendar.CalendarAdapter
import net.blueshell.api.domain.event.application.calendar.CalendarEventData
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.platform.integration.queue.CalendarJobs
import org.springframework.stereotype.Component

/**
 * Job handler for adding events to external calendar.
 *
 * Uses CalendarAdapter (ADR-019 ACL) to isolate from specific calendar provider.
 */
@Component
class AddEventToCalendarJob(
    objectMapper: ObjectMapper,
    private val calendarAdapter: CalendarAdapter,
    private val events: EventService
) : AbstractJsonJobHandler<CalendarEventRef>(objectMapper, CalendarJobs.AddEvent.payloadType) {
    override val jobType: String = CalendarJobs.AddEvent.type

    override fun handlePayload(payload: CalendarEventRef) {
        val event = events.findById(payload.eventId)
        if (!event.approved) return

        // Use adapter to add event to external calendar
        val eventData = CalendarEventData(
            title = event.title,
            location = event.location,
            description = event.description,
            startTime = event.startTime,
            endTime = event.endTime,
            approved = event.approved
        )

        val ref = calendarAdapter.addEvent(event.id!!, eventData)
        event.googleId = ref.externalId
        events.update(event)
    }

}
