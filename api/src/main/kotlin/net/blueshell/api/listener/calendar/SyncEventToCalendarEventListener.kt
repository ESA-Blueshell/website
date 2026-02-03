package net.blueshell.api.listener.calendar

import net.blueshell.api.common.event.job.SyncEventToCalendarEvent
import net.blueshell.api.job.calendar.SyncEventToCalendarJob
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class SyncEventToCalendarEventListener(
    private val job: SyncEventToCalendarJob
) {
    @EventListener
    fun onSync(evt: SyncEventToCalendarEvent) {
        val id = evt.eventId
        if (id == null) return
        job.sync(id)
    }
}
