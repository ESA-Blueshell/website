package net.blueshell.api.listener.calendar;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.common.event.job.AddEventToCalendarEvent;
import net.blueshell.api.job.calendar.AddEventToCalendarJob;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AddEventToCalendarEventListener {

    private final AddEventToCalendarJob job;

    @EventListener
    public void onAdd(AddEventToCalendarEvent evt) {
        Long id = evt.eventId();
        if (id == null) return;
        job.add(id);
    }
}