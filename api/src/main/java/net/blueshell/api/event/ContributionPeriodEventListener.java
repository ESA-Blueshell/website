package net.blueshell.api.event;

import net.blueshell.api.common.event.PostPersistEvent;
import net.blueshell.api.common.event.PrePersistEvent;
import net.blueshell.api.model.ContributionPeriod;
import net.blueshell.api.service.ContributionPeriodService;
import net.blueshell.api.service.brevo.ContactService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ContributionPeriodEventListener {

    private final ContactService contacts;
    private final ContributionPeriodService periods;

    public ContributionPeriodEventListener(ContactService contacts, ContributionPeriodService periods) {
        this.contacts = contacts;
        this.periods = periods;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onContributionPeriodCreated(PostPersistEvent<ContributionPeriod> evt) {
        var c = evt.getSource();
        if (c.getListId() != null) return;

        contacts.createList(c);
        periods.update(c);
    }
}
