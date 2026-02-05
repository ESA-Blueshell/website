package net.blueshell.api.listener.calendar

import net.blueshell.api.common.event.job.AddEventToCalendarEvent
import net.blueshell.api.job.calendar.AddEventToCalendarJob
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
