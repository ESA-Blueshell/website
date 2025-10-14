package net.blueshell.api.listener.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.common.event.job.EventSignupEmailEvent;
import net.blueshell.api.job.email.EventSignupEmailJob;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventSignupEmailEventListener {

    private final EventSignupEmailJob job;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSend(EventSignupEmailEvent evt) {
        Long id = evt.eventSignUpId();
        if (id == null) return;
        job.send(id);
    }
}