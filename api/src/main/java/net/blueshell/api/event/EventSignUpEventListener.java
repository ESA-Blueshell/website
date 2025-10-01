package net.blueshell.api.event;

import net.blueshell.api.common.event.PrePersistEvent;
import net.blueshell.api.model.event.EventSignUp;
import net.blueshell.api.service.email.EmailService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class EventSignUpEventListener {

    private final EmailService emails;

    public EventSignUpEventListener(EmailService emails) {
        this.emails = emails;
    }

    /**
     * send e-mail only if the transaction COMMITTED successfully
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPersist(PrePersistEvent<EventSignUp> evt) {
        var e = evt.getSource();

        if (e.getGuest() != null) {
            emails.eventSignUp(e);
        }
    }
}
