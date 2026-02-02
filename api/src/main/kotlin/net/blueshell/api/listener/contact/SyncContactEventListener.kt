package net.blueshell.api.listener.contact;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.common.event.job.SyncContactEvent;
import net.blueshell.api.job.contact.SyncContactJob;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SyncContactEventListener {

    private final SyncContactJob job;

    @EventListener
    public void onSync(SyncContactEvent evt) {
        Long userId = evt.userId();
        if (userId == null) return;
        job.sync(userId);
    }
}