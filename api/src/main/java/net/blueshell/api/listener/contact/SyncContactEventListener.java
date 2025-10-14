package net.blueshell.api.listener.contact;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.common.event.job.SyncContactEvent;
import net.blueshell.api.job.contact.SyncContactJob;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class SyncContactEventListener {

    private final SyncContactJob job;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSync(SyncContactEvent evt) {
        Long userId = evt.userId();
        if (userId == null) return;
        job.sync(userId);
    }
}