package net.blueshell.api.event;

import net.blueshell.api.common.event.PostPersistEvent;
import net.blueshell.api.common.event.PostRemoveEvent;
import net.blueshell.api.common.event.PostUpdateEvent;
import net.blueshell.api.job.brevo.RemoveContactFromListJob;
import net.blueshell.api.model.contribution.Contribution;
import net.blueshell.api.service.brevo.ContactService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ContributionEventListener {

    private final ContactService contacts;
    private final RemoveContactFromListJob removeContactFromListJob;

    public ContributionEventListener(ContactService contacts, RemoveContactFromListJob removeContactFromListJob) {
        this.contacts = contacts;
        this.removeContactFromListJob = removeContactFromListJob;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPersist(PostPersistEvent<Contribution> evt) {
        var c = evt.getSource();
        contacts.addToList(c.getContributionPeriod(), c.getUser());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUpdate(PostUpdateEvent<Contribution> evt) {
        var c = evt.getSource();
        contacts.addToList(c.getContributionPeriod(), c.getUser());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDelete(PostRemoveEvent<Contribution> evt) {
        var c = evt.getSource();
        removeContactFromListJob.removeFromList(c.getUserId(), c.getContributionPeriodId());
    }
}
