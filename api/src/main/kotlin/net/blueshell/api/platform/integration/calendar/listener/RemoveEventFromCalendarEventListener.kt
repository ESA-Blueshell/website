package net.blueshell.api.platform.integration.calendar.listener

import net.blueshell.api.platform.integration.event.job.RemoveEventFromCalendarEvent
import net.blueshell.api.platform.integration.calendar.job.RemoveEventFromCalendarJob
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
