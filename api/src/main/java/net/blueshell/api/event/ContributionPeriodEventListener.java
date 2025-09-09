package net.blueshell.api.event;

import net.blueshell.api.common.event.PreInsertEvent;
import net.blueshell.api.model.ContributionPeriod;
import net.blueshell.api.service.brevo.ContactService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ContributionPeriodEventListener {

    private final ContactService contacts;

    public ContributionPeriodEventListener(ContactService contacts) {
        this.contacts = contacts;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onContributionPeriodCreated(PreInsertEvent<ContributionPeriod> evt) {
        var c = evt.getSource();
        var listId = contacts.createList(c);
        c.setListId(listId);
    }
}
