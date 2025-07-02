package net.blueshell.api.event;

import net.blueshell.api.common.event.EntityCreatedEvent;
import net.blueshell.api.model.ContributionPeriod;
import net.blueshell.api.service.brevo.ContactService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import sendinblue.ApiException;

@Component
public class ContributionPeriodEventHandler {

    private final ContactService contacts;

    public ContributionPeriodEventHandler(ContactService contacts) {
        this.contacts = contacts;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onContributionPeriodCreated(EntityCreatedEvent<ContributionPeriod> evt) throws ApiException {
        var c = evt.entity();
        var listId = contacts.createList(c);
        c.setListId(listId);
    }
}
