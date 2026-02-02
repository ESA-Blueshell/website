package net.blueshell.api.listener.jpa

import lombok.RequiredArgsConstructor
import net.blueshell.api.common.event.job.CreateContributionPeriodListEvent
import net.blueshell.api.common.event.jpa.PostPersistEvent
import net.blueshell.api.common.event.jpa.PostUpdateEvent
import net.blueshell.api.model.contribution.ContributionPeriod
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
@RequiredArgsConstructor
class ContributionPeriodEventListener {
    private val eventPublisher: ApplicationEventPublisher? = null

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onUpdate(evt: PostUpdateEvent<ContributionPeriod>) {
        val c = evt.getSource()
        if (c.getListId() != null) return
        eventPublisher!!.publishEvent(CreateContributionPeriodListEvent(c.getId()))
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onCreate(evt: PostPersistEvent<ContributionPeriod>) {
        val c = evt.getSource()
        if (c.getListId() != null) return
        eventPublisher!!.publishEvent(CreateContributionPeriodListEvent(c.getId()))
    }
}
