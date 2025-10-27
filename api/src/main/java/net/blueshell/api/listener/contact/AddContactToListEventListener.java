package net.blueshell.api.listener.contact;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.common.event.job.AddContactToListEvent;
import net.blueshell.api.job.contact.AddContactToListJob;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AddContactToListEventListener {

    private final AddContactToListJob job;

    @EventListener
    public void onAdd(AddContactToListEvent evt) {
        Long userId = evt.userId();
        Long periodId = evt.periodId();
        if (userId == null || periodId == null) return;
        job.addToList(userId, periodId);
    }
}