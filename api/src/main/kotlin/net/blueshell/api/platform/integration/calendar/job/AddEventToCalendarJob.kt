package net.blueshell.api.platform.integration.calendar.job

import com.fasterxml.jackson.databind.ObjectMapper
import net.blueshell.api.event.application.EventService
import net.blueshell.api.platform.integration.calendar.CalendarService
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import org.springframework.stereotype.Component

@Component
class AddEventToCalendarJob(
    objectMapper: ObjectMapper,
    private val calendar: CalendarService,
    private val events: EventService
) : AbstractJsonJobHandler<CalendarEventRef>(objectMapper, CalendarEventRef::class.java) {
    override val jobType: String = TYPE

    override fun handlePayload(payload: CalendarEventRef) {
        val event = events.findById(payload.eventId)
        if (!event.approved) return
        calendar.add(event)
        events.update(event)
    }

    companion object {
        const val TYPE = "calendar.add-event"
    }
}
