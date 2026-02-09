package net.blueshell.api.contribution.listener

import net.blueshell.api.platform.integration.event.job.CreateContributionPeriodListEvent
import net.blueshell.api.shared.event.jpa.PostPersistEvent
import net.blueshell.api.shared.event.jpa.PostUpdateEvent
import net.blueshell.api.contribution.model.ContributionPeriod
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class ContributionPeriodEventListener(
    private val eventPublisher: ApplicationEventPublisher
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onUpdate(evt: PostUpdateEvent<ContributionPeriod>) {
        val c = evt.source
        if (c.listId != null) return
        eventPublisher.publishEvent(CreateContributionPeriodListEvent(c.id))
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onCreate(evt: PostPersistEvent<ContributionPeriod>) {
        val c = evt.source
        if (c.listId != null) return
        eventPublisher.publishEvent(CreateContributionPeriodListEvent(c.id))
    }
}
