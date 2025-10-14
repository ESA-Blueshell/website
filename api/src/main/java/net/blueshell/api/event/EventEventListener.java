package net.blueshell.api.event;

import net.blueshell.api.common.event.PostRemoveEvent;
import net.blueshell.api.common.event.PostUpdateEvent;
import net.blueshell.api.common.event.PrePersistEvent;
import net.blueshell.api.job.calendar.AddEventToCalendarJob;
import net.blueshell.api.job.calendar.RemoveEventFromCalendarJob;
import net.blueshell.api.job.calendar.SyncEventToCalendarJob;
import net.blueshell.api.model.event.Event;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class EventEventListener {

    private final AddEventToCalendarJob addJob;
    private final SyncEventToCalendarJob syncJob;
    private final RemoveEventFromCalendarJob removeJob;

    public EventEventListener(AddEventToCalendarJob addJob,
                              SyncEventToCalendarJob syncJob,
                              RemoveEventFromCalendarJob removeJob) {
        this.addJob = addJob;
        this.syncJob = syncJob;
        this.removeJob = removeJob;
    }

    /**
     * After commit, enqueue add if approved
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPersist(PrePersistEvent<Event> evt) {
        Event e = evt.getSource();
        if (e.isApproved()) {
            addJob.add(e.getId());
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
            syncJob.sync(e.getId());
        } else {
            removeJob.remove(e.getId());
        }
    }

    /**
     * After commit, enqueue remove
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDelete(PostRemoveEvent<Event> evt) {
        Event e = evt.getSource();
        removeJob.remove(e.getId());
    }
}
