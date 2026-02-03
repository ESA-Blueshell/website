package net.blueshell.api.listener.calendar

import net.blueshell.api.common.event.job.UpdateEventInCalendarEvent
import net.blueshell.api.job.calendar.UpdateEventInCalendarJob
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class UpdateEventInCalendarEventListener(
    private val job: UpdateEventInCalendarJob
) {
    @EventListener
    fun onUpdate(evt: UpdateEventInCalendarEvent) {
        val id = evt.eventId
        if (id == null) return
        job.update(id)
    }
}
