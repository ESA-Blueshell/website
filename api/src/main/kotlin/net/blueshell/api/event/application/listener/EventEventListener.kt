package net.blueshell.api.event.application.listener

import net.blueshell.api.event.application.EventService
import net.blueshell.api.event.application.event.EventChangeType
import net.blueshell.api.event.application.event.EventChangedEvent
import net.blueshell.api.platform.integration.calendar.job.AddEventToCalendarJobHandler
import net.blueshell.api.platform.integration.calendar.job.CalendarEventPayload
import net.blueshell.api.platform.integration.calendar.job.RemoveEventFromCalendarJobHandler
import net.blueshell.api.platform.integration.calendar.job.SyncEventToCalendarJobHandler
import net.blueshell.api.platform.integration.queue.JobDispatcher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class EventEventListener(
    private val jobDispatcher: JobDispatcher,
    private val events: EventService
) {
    /**
     * After commit, enqueue add if approved
     */
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onChange(evt: EventChangedEvent) {
        when (evt.changeType) {
            EventChangeType.CREATED -> {
                val e = events.findById(evt.eventId)
                if (e.approved) {
                    jobDispatcher.enqueue(
                        AddEventToCalendarJobHandler.JOB_TYPE,
                        CalendarEventPayload(e.id!!)
                    )
                }
            }
            EventChangeType.UPDATED -> {
                val e = events.findById(evt.eventId)
                if (e.approved) {
                    jobDispatcher.enqueue(
                        SyncEventToCalendarJobHandler.JOB_TYPE,
                        CalendarEventPayload(e.id!!)
                    )
                } else {
                    jobDispatcher.enqueue(
                        RemoveEventFromCalendarJobHandler.JOB_TYPE,
                        CalendarEventPayload(e.id!!)
                    )
                }
            }
            EventChangeType.DELETED -> jobDispatcher.enqueue(
                RemoveEventFromCalendarJobHandler.JOB_TYPE,
                CalendarEventPayload(evt.eventId)
            )
        }
    }
}
