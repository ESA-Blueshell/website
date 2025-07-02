package net.blueshell.api.event;

import net.blueshell.api.common.event.EntityCreatedEvent;
import net.blueshell.api.common.event.EntityDeletedEvent;
import net.blueshell.api.model.Contribution;
import net.blueshell.api.model.ContributionPeriod;
import net.blueshell.api.service.brevo.ContactService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import sendinblue.ApiException;

@Component
public class ContributionEventHandler {

    private final ContactService contacts;

    public ContributionEventHandler(ContactService contacts) {
        this.contacts = contacts;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onContributionCreated(EntityCreatedEvent<Contribution> evt) throws ApiException {
        var c = evt.entity();
        contacts.addToList(c.getContributionPeriod(), c.getUser());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onContributionDeleted(EntityDeletedEvent<Contribution> evt) throws ApiException {
        var c = evt.entity();
        contacts.removeFromList(c.getContributionPeriod(), c.getUser());
    }
}
