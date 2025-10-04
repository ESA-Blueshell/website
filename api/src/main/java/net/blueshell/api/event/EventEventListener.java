package net.blueshell.api.event;

import net.blueshell.api.common.event.PostRemoveEvent;
import net.blueshell.api.common.event.PostUpdateEvent;
import net.blueshell.api.common.event.PrePersistEvent;
import net.blueshell.api.model.event.Event;
import net.blueshell.api.service.google.CalendarService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.io.IOException;

@Component
public class EventEventListener {

    private final CalendarService calendars;

    public EventEventListener(CalendarService calendars) {
        this.calendars = calendars;
    }

    /**
     * send e-mail only if the transaction COMMITTED successfully
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onEventCreated(PrePersistEvent<Event> evt) throws IOException {
        Event e = evt.getSource();
        if (e.isApproved()) {
            calendars.add(e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onEventUpdated(PostUpdateEvent<Event> evt) throws IOException {
        Event e = evt.getSource();
        if (e.isApproved()) {
            calendars.sync(e);
        } else {
            // Remove from calendar if no longer visible
            calendars.remove(e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onEventDeleted(PostRemoveEvent<Event> evt) throws IOException {
        Event e = evt.getSource();

        calendars.remove(e);
    }
}
