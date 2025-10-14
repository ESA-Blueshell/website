package net.blueshell.api.listener.jpa;

import net.blueshell.api.common.event.job.EventSignupEmailEvent;
import net.blueshell.api.common.event.jpa.PrePersistEvent;
import net.blueshell.api.model.event.EventSignUp;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class EventSignUpEventListener {

    private final ApplicationEventPublisher eventPublisher;

    public EventSignUpEventListener(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * send e-mail only if the transaction COMMITTED successfully
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPersist(PrePersistEvent<EventSignUp> evt) {
        var e = evt.getSource();

        if (e.getGuest() != null) {
            eventPublisher.publishEvent(new EventSignupEmailEvent(e.getId()));
        }
    }
}
