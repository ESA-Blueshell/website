package net.blueshell.api.listener.calendar;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.common.event.job.RemoveEventFromCalendarEvent;
import net.blueshell.api.job.calendar.RemoveEventFromCalendarJob;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class RemoveEventFromCalendarEventListener {

    private final RemoveEventFromCalendarJob job;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRemove(RemoveEventFromCalendarEvent evt) {
        Long id = evt.eventId();
        if (id == null) return;
        job.remove(id);
    }
}