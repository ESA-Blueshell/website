package net.blueshell.api.listener.calendar

import lombok.RequiredArgsConstructor
import lombok.extern.slf4j.Slf4j
import net.blueshell.api.common.event.job.UpdateEventInCalendarEvent
import net.blueshell.api.job.calendar.UpdateEventInCalendarJob
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Slf4j
@Component
@RequiredArgsConstructor
class UpdateEventInCalendarEventListener {
    private val job: UpdateEventInCalendarJob? = null

    @EventListener
    fun onUpdate(evt: UpdateEventInCalendarEvent) {
        val id = evt.eventId
        if (id == null) return
        job!!.update(id)
    }
}