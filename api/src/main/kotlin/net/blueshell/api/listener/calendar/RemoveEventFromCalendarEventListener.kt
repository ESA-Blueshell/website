package net.blueshell.api.listener.calendar

import net.blueshell.api.common.event.job.RemoveEventFromCalendarEvent
import net.blueshell.api.job.calendar.RemoveEventFromCalendarJob
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class RemoveEventFromCalendarEventListener(
    private val job: RemoveEventFromCalendarJob
) {
    @EventListener
    fun onRemove(evt: RemoveEventFromCalendarEvent) {
        val id = evt.eventId ?: return
        job.remove(id)
    }
}
