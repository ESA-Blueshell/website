package net.blueshell.api.event;

import net.blueshell.api.common.event.PostPersistEvent;
import net.blueshell.api.common.event.PostUpdateEvent;
import net.blueshell.api.job.contact.CreateContributionPeriodListJob;
import net.blueshell.api.model.contribution.ContributionPeriod;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ContributionPeriodEventListener {

    private final CreateContributionPeriodListJob createListJob;

    public ContributionPeriodEventListener(CreateContributionPeriodListJob createListJob) {
        this.createListJob = createListJob;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUpdate(PostUpdateEvent<ContributionPeriod> evt) {
        var c = evt.getSource();
        if (c.getListId() != null) return;
        createListJob.createList(c.getId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCreate(PostPersistEvent<ContributionPeriod> evt) {
        var c = evt.getSource();
        if (c.getListId() != null) return;
        createListJob.createList(c.getId());
    }
}
