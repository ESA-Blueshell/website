package net.blueshell.api.listener.jpa;

import net.blueshell.api.common.event.job.AddEventToCalendarEvent;
import net.blueshell.api.common.event.job.RemoveEventFromCalendarEvent;
import net.blueshell.api.common.event.job.SyncEventToCalendarEvent;
import net.blueshell.api.common.event.jpa.PostRemoveEvent;
import net.blueshell.api.common.event.jpa.PostUpdateEvent;
import net.blueshell.api.common.event.jpa.PrePersistEvent;
import net.blueshell.api.model.event.Event;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class EventEventListener {

    private final ApplicationEventPublisher eventPublisher;

    public EventEventListener(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * After commit, enqueue add if approved
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPersist(PrePersistEvent<Event> evt) {
        Event e = evt.getSource();
        if (e.isApproved()) {
            eventPublisher.publishEvent(new AddEventToCalendarEvent(e.getId()));
        }
    }

    /**
     * After commit, enqueue sync if approved, otherwise remove
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUpdate(PostUpdateEvent<Event> evt) {
        Event e = evt.getSource();
        if (e.isApproved()) {
            eventPublisher.publishEvent(new SyncEventToCalendarEvent(e.getId()));
        } else {
            eventPublisher.publishEvent(new RemoveEventFromCalendarEvent(e.getId()));
        }
    }

    /**
     * After commit, enqueue remove
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDelete(PostRemoveEvent<Event> evt) {
        Event e = evt.getSource();
        eventPublisher.publishEvent(new RemoveEventFromCalendarEvent(e.getId()));
    }
}
