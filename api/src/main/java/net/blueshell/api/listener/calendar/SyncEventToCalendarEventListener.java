package net.blueshell.api.listener.calendar;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.common.event.job.SyncEventToCalendarEvent;
import net.blueshell.api.job.calendar.SyncEventToCalendarJob;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class SyncEventToCalendarEventListener {

    private final SyncEventToCalendarJob job;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSync(SyncEventToCalendarEvent evt) {
        Long id = evt.eventId();
        if (id == null) return;
        job.sync(id);
    }
}