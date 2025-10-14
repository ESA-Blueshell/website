package net.blueshell.api.listener.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.common.event.job.ContributionReminderEmailEvent;
import net.blueshell.api.job.email.ContributionReminderEmailJob;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContributionReminderEmailEventListener {

    private final ContributionReminderEmailJob job;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSend(ContributionReminderEmailEvent evt) {
        Long reminderId = evt.reminderId();
        if (reminderId == null) return;
        job.send(reminderId);
    }
}