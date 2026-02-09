package net.blueshell.api.platform.integration.calendar.job

import com.fasterxml.jackson.databind.ObjectMapper
import net.blueshell.api.event.application.EventService
import net.blueshell.api.platform.integration.calendar.CalendarService
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import org.springframework.stereotype.Component

@Component
class SyncEventToCalendarJob(
    objectMapper: ObjectMapper,
    private val calendar: CalendarService,
    private val events: EventService
) : AbstractJsonJobHandler<CalendarEventRef>(objectMapper, CalendarEventRef::class.java) {
    override val jobType: String = TYPE

    override fun handlePayload(payload: CalendarEventRef) {
        val event = events.findById(payload.eventId)

        if (!event.approved) {
            if (event.googleId != null) {
                calendar.remove(event)
                events.update(event)
            }
            return
        }

        if (event.googleId == null) {
            calendar.add(event)
        } else {
            calendar.update(event)
        }
        events.update(event)
    }

    companion object {
        const val TYPE = "calendar.sync-event"
    }
}
