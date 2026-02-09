package net.blueshell.api.event.application.listener

import net.blueshell.api.event.application.EventService
import net.blueshell.api.event.application.event.EventChangeType
import net.blueshell.api.event.application.event.EventChangedEvent
import net.blueshell.api.platform.integration.event.job.AddEventToCalendarEvent
import net.blueshell.api.platform.integration.event.job.RemoveEventFromCalendarEvent
import net.blueshell.api.platform.integration.event.job.SyncEventToCalendarEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class EventEventListener(
    private val eventPublisher: ApplicationEventPublisher,
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
                    eventPublisher.publishEvent(AddEventToCalendarEvent(e.id))
                }
            }
            EventChangeType.UPDATED -> {
                val e = events.findById(evt.eventId)
                if (e.approved) {
                    eventPublisher.publishEvent(SyncEventToCalendarEvent(e.id))
                } else {
                    eventPublisher.publishEvent(RemoveEventFromCalendarEvent(e.id))
                }
            }
            EventChangeType.DELETED -> eventPublisher.publishEvent(RemoveEventFromCalendarEvent(evt.eventId))
        }
    }
}
