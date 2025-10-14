package net.blueshell.api.event;

import net.blueshell.api.common.event.PrePersistEvent;
import net.blueshell.api.job.email.EventSignupEmailJob;
import net.blueshell.api.model.event.EventSignUp;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class EventSignUpEventListener {

    private final EventSignupEmailJob emailJob;

    public EventSignUpEventListener(EventSignupEmailJob emailJob) {
        this.emailJob = emailJob;
    }

    /**
     * send e-mail only if the transaction COMMITTED successfully
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPersist(PrePersistEvent<EventSignUp> evt) {
        var e = evt.getSource();

        if (e.getGuest() != null) {
            emailJob.send(e.getId());
        }
    }
}
