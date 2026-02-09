package net.blueshell.api.platform.integration.calendar.listener

import net.blueshell.api.platform.integration.event.job.UpdateEventInCalendarEvent
import net.blueshell.api.platform.integration.calendar.job.UpdateEventInCalendarJob
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class UpdateEventInCalendarEventListener(
    private val job: UpdateEventInCalendarJob
) {
    @EventListener
    fun onUpdate(evt: UpdateEventInCalendarEvent) {
        val id = evt.eventId ?: return
        job.update(id)
    }
}
