package net.blueshell.api.platform.integration.calendar.job

import com.fasterxml.jackson.databind.ObjectMapper
import net.blueshell.api.domain.event.application.EventService
import net.blueshell.api.platform.integration.calendar.CalendarService
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.platform.integration.queue.CalendarJobs
import org.springframework.stereotype.Component

@Component
class SyncEventToCalendarJob(
    objectMapper: ObjectMapper,
    private val calendar: CalendarService,
    private val events: EventService
) : AbstractJsonJobHandler<CalendarEventRef>(objectMapper, CalendarJobs.SyncEvent.payloadType) {
    override val jobType: String = CalendarJobs.SyncEvent.type

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

}
