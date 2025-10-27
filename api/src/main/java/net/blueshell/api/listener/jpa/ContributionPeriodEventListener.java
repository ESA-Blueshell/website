package net.blueshell.api.listener.jpa;

import lombok.RequiredArgsConstructor;
import net.blueshell.api.common.event.job.CreateContributionPeriodListEvent;
import net.blueshell.api.common.event.jpa.PostPersistEvent;
import net.blueshell.api.common.event.jpa.PostUpdateEvent;
import net.blueshell.api.model.contribution.ContributionPeriod;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ContributionPeriodEventListener {

    private final ApplicationEventPublisher eventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUpdate(PostUpdateEvent<ContributionPeriod> evt) {
        var c = evt.getSource();
        if (c.getListId() != null) return;
        eventPublisher.publishEvent(new CreateContributionPeriodListEvent(c.getId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCreate(PostPersistEvent<ContributionPeriod> evt) {
        var c = evt.getSource();
        if (c.getListId() != null) return;
        eventPublisher.publishEvent(new CreateContributionPeriodListEvent(c.getId()));
    }
}
