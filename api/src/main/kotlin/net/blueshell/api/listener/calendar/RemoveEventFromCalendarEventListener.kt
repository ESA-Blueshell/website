package net.blueshell.api.listener.calendar;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.common.event.job.RemoveEventFromCalendarEvent;
import net.blueshell.api.job.calendar.RemoveEventFromCalendarJob;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RemoveEventFromCalendarEventListener {

    private final RemoveEventFromCalendarJob job;

    @EventListener
    public void onRemove(RemoveEventFromCalendarEvent evt) {
        Long id = evt.eventId();
        if (id == null) return;
        job.remove(id);
    }
}