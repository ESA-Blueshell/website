package net.blueshell.api.platform.integration.calendar.job

import com.fasterxml.jackson.databind.ObjectMapper
import net.blueshell.api.domain.event.application.EventService
import net.blueshell.api.platform.integration.calendar.CalendarService
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.platform.integration.queue.CalendarJobs
import org.springframework.stereotype.Component

@Component
class AddEventToCalendarJob(
    objectMapper: ObjectMapper,
    private val calendar: CalendarService,
    private val events: EventService
) : AbstractJsonJobHandler<CalendarEventRef>(objectMapper, CalendarJobs.AddEvent.payloadType) {
    override val jobType: String = CalendarJobs.AddEvent.type

    override fun handlePayload(payload: CalendarEventRef) {
        val event = events.findById(payload.eventId)
        if (!event.approved) return
        calendar.add(event)
        events.update(event)
    }

}
