package net.blueshell.api.event;

import net.blueshell.api.common.event.EntityCreatedEvent;
import net.blueshell.api.common.event.EntityDeletedEvent;
import net.blueshell.api.common.event.EntityUpdatedEvent;
import net.blueshell.api.model.Event;
import net.blueshell.api.service.google.CalendarService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.io.IOException;

@Component
public class EventEventHandler {

    private final CalendarService calendars;

    public EventEventHandler(CalendarService calendars) {
        this.calendars = calendars;
    }

    /**
     * send e-mail only if the transaction COMMITTED successfully
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEventCreated(EntityCreatedEvent<Event> evt) throws IOException {
        Event e = evt.entity();
        if (e.isVisible()) {
            calendars.add(e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEventUpdated(EntityUpdatedEvent<Event> evt) throws IOException {
        Event e = evt.entity();
        if (e.isVisible()) {
            if (e.getGoogleId() != null) {
                calendars.update(e);
            } else {
                calendars.add(e);
            }
        } else if (e.getGoogleId() != null) {
            // Remove from calendar if no longer visible
            calendars.remove(e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onEventDeleted(EntityDeletedEvent<Event> evt) throws IOException {
        Event e = evt.entity();
        if (e.getGoogleId() != null) {
            calendars.remove(e);
        }
    }

    /**
     * clean-up if the outer transaction rolls back
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void onFailure(EntityCreatedEvent<Event> evt) {
        Event e = evt.entity();
    }
}

