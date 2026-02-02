package net.blueshell.api.listener.calendar

import lombok.RequiredArgsConstructor
import lombok.extern.slf4j.Slf4j
import net.blueshell.api.common.event.job.SyncEventToCalendarEvent
import net.blueshell.api.job.calendar.SyncEventToCalendarJob
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Slf4j
@Component
@RequiredArgsConstructor
class SyncEventToCalendarEventListener {
    private val job: SyncEventToCalendarJob? = null

    @EventListener
    fun onSync(evt: SyncEventToCalendarEvent) {
        val id = evt.eventId
        if (id == null) return
        job!!.sync(id)
    }
}