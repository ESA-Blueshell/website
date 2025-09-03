package net.blueshell.api.event;

import net.blueshell.api.common.event.PreInsertEvent;
import net.blueshell.api.common.event.PostDeleteEvent;
import net.blueshell.api.common.event.PostUpdateEvent;
import net.blueshell.api.model.Event;
import net.blueshell.api.service.google.CalendarService;
import org.springframework.stereotype.Component;
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
    public void onEventCreated(PreInsertEvent<Event> evt) throws IOException {
        Event e = evt.getSource();
        if (e.isVisible()) {
            calendars.add(e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEventUpdated(PostUpdateEvent<Event> evt) throws IOException {
        Event e = evt.getSource();
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
    public void onEventDeleted(PostDeleteEvent<Event> evt) throws IOException {
        Event e = evt.getSource();
        if (e.getGoogleId() != null) {
            calendars.remove(e);
        }
    }

    /**
     * clean-up if the outer transaction rolls back
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void onFailure(PreInsertEvent<Event> evt) {
        Event e = evt.getSource();
    }
}

