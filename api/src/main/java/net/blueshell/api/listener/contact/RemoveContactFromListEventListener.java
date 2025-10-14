package net.blueshell.api.listener.contact;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.common.event.job.RemoveContactFromListEvent;
import net.blueshell.api.job.contact.RemoveContactFromListJob;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class RemoveContactFromListEventListener {

    private final RemoveContactFromListJob job;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRemove(RemoveContactFromListEvent evt) {
        Long userId = evt.userId();
        Long periodId = evt.periodId();
        if (userId == null || periodId == null) return;
        job.removeFromList(userId, periodId);
    }
}