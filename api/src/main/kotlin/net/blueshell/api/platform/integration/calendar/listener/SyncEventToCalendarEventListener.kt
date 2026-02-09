package net.blueshell.api.platform.integration.calendar.listener

import net.blueshell.api.platform.integration.event.job.SyncEventToCalendarEvent
import net.blueshell.api.platform.integration.calendar.job.SyncEventToCalendarJob
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class SyncEventToCalendarEventListener(
    private val job: SyncEventToCalendarJob
) {
    @EventListener
    fun onSync(evt: SyncEventToCalendarEvent) {
        val id = evt.eventId ?: return
        job.sync(id)
    }
}
