package net.blueshell.api.listener.calendar

import lombok.RequiredArgsConstructor
import lombok.extern.slf4j.Slf4j
import net.blueshell.api.common.event.job.RemoveEventFromCalendarEvent
import net.blueshell.api.job.calendar.RemoveEventFromCalendarJob
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Slf4j
@Component
@RequiredArgsConstructor
class RemoveEventFromCalendarEventListener {
    private val job: RemoveEventFromCalendarJob? = null

    @EventListener
    fun onRemove(evt: RemoveEventFromCalendarEvent) {
        val id = evt.eventId
        if (id == null) return
        job!!.remove(id)
    }
}