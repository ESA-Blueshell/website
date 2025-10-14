package net.blueshell.api.listener.jpa;

import net.blueshell.api.common.event.job.AddContactToListEvent;
import net.blueshell.api.common.event.job.RemoveContactFromListEvent;
import net.blueshell.api.common.event.jpa.PostPersistEvent;
import net.blueshell.api.common.event.jpa.PostRemoveEvent;
import net.blueshell.api.common.event.jpa.PostUpdateEvent;
import net.blueshell.api.model.contribution.Contribution;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ContributionEventListener {

    private final ApplicationEventPublisher eventPublisher;

    public ContributionEventListener(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPersist(PostPersistEvent<Contribution> evt) {
        var c = evt.getSource();
        eventPublisher.publishEvent(new AddContactToListEvent(c.getUserId(), c.getContributionPeriodId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUpdate(PostUpdateEvent<Contribution> evt) {
        var c = evt.getSource();
        eventPublisher.publishEvent(new AddContactToListEvent(c.getUserId(), c.getContributionPeriodId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDelete(PostRemoveEvent<Contribution> evt) {
        var c = evt.getSource();
        eventPublisher.publishEvent(new RemoveContactFromListEvent(c.getUserId(), c.getContributionPeriodId()));
    }
}
