package net.blueshell.api.platform.integration.calendar.job

import com.fasterxml.jackson.databind.ObjectMapper
import net.blueshell.api.domain.event.application.EventService
import net.blueshell.api.domain.event.application.calendar.CalendarAdapter
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.platform.integration.queue.CalendarJobs
import org.springframework.stereotype.Component

/**
 * Job handler for removing events from external calendar.
 *
 * Uses CalendarAdapter (ADR-019 ACL) to isolate from specific calendar provider.
 */
@Component
class RemoveEventFromCalendarJob(
    objectMapper: ObjectMapper,
    private val calendarAdapter: CalendarAdapter,
    private val events: EventService
) : AbstractJsonJobHandler<CalendarEventRef>(objectMapper, CalendarJobs.RemoveEvent.payloadType) {
    override val jobType: String = CalendarJobs.RemoveEvent.type

    override fun handlePayload(payload: CalendarEventRef) {
        val event = events.findById(payload.eventId)
        if (event.googleId == null) return

        // Use adapter to remove event from external calendar
        calendarAdapter.removeEvent(event.id!!, event.googleId!!)
        event.googleId = null
        events.update(event)
    }

}
