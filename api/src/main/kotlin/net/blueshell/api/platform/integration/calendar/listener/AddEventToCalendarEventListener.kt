package net.blueshell.api.platform.integration.calendar.listener

import net.blueshell.api.platform.integration.event.job.AddEventToCalendarEvent
import net.blueshell.api.platform.integration.calendar.job.AddEventToCalendarJob
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class AddEventToCalendarEventListener(
    private val job: AddEventToCalendarJob
) {
    @EventListener
    fun onAdd(evt: AddEventToCalendarEvent) {
        val id = evt.eventId ?: return
        job.add(id)
    }
}
