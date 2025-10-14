package net.blueshell.api.listener.contact;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.common.event.job.CreateContributionPeriodListEvent;
import net.blueshell.api.job.contact.CreateContributionPeriodListJob;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class CreateContributionPeriodListEventListener {

    private final CreateContributionPeriodListJob job;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCreate(CreateContributionPeriodListEvent evt) {
        Long periodId = evt.periodId();
        if (periodId == null) return;
        job.createList(periodId);
    }
}