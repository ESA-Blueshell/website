package net.blueshell.api.contribution.application.listener

import net.blueshell.api.platform.integration.event.job.AddContactToListEvent
import net.blueshell.api.platform.integration.event.job.RemoveContactFromListEvent
import net.blueshell.api.shared.event.jpa.PostPersistEvent
import net.blueshell.api.shared.event.jpa.PostRemoveEvent
import net.blueshell.api.shared.event.jpa.PostUpdateEvent
import net.blueshell.api.contribution.domain.model.Contribution
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class ContributionEventListener(
    private val eventPublisher: ApplicationEventPublisher
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onPersist(evt: PostPersistEvent<Contribution>) {
        val c = evt.source
        eventPublisher.publishEvent(AddContactToListEvent(c.userId, c.contributionPeriodId))
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onUpdate(evt: PostUpdateEvent<Contribution>) {
        val c = evt.source
        eventPublisher.publishEvent(AddContactToListEvent(c.userId, c.contributionPeriodId))
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onDelete(evt: PostRemoveEvent<Contribution>) {
        val c = evt.source
        eventPublisher.publishEvent(RemoveContactFromListEvent(c.userId, c.contributionPeriodId))
    }
}
