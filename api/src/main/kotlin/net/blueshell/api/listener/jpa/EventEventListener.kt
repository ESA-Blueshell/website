package net.blueshell.api.listener.jpa

import net.blueshell.api.common.event.job.AddEventToCalendarEvent
import net.blueshell.api.common.event.job.RemoveEventFromCalendarEvent
import net.blueshell.api.common.event.job.SyncEventToCalendarEvent
import net.blueshell.api.common.event.jpa.PostRemoveEvent
import net.blueshell.api.common.event.jpa.PostUpdateEvent
import net.blueshell.api.common.event.jpa.PrePersistEvent
import net.blueshell.api.model.event.Event
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class EventEventListener(
    private val eventPublisher: ApplicationEventPublisher
) {
    /**
     * After commit, enqueue add if approved
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onPersist(evt: PrePersistEvent<Event>) {
        val e = evt.source
        if (e.approved) {
            eventPublisher.publishEvent(AddEventToCalendarEvent(e.id))
        }
    }

    /**
     * After commit, enqueue sync if approved, otherwise remove
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onUpdate(evt: PostUpdateEvent<Event>) {
        val e = evt.source
        if (e.approved) {
            eventPublisher.publishEvent(SyncEventToCalendarEvent(e.id))
        } else {
            eventPublisher.publishEvent(RemoveEventFromCalendarEvent(e.id))
        }
    }

    /**
     * After commit, enqueue remove
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onDelete(evt: PostRemoveEvent<Event>) {
        val e = evt.source
        eventPublisher.publishEvent(RemoveEventFromCalendarEvent(e.id))
    }
}
