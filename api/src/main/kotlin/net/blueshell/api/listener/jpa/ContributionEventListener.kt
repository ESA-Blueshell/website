package net.blueshell.api.listener.jpa

import lombok.RequiredArgsConstructor
import net.blueshell.api.common.event.job.AddContactToListEvent
import net.blueshell.api.common.event.job.RemoveContactFromListEvent
import net.blueshell.api.common.event.jpa.PostPersistEvent
import net.blueshell.api.common.event.jpa.PostRemoveEvent
import net.blueshell.api.common.event.jpa.PostUpdateEvent
import net.blueshell.api.model.contribution.Contribution
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
@RequiredArgsConstructor
class ContributionEventListener {
    private val eventPublisher: ApplicationEventPublisher? = null

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onPersist(evt: PostPersistEvent<Contribution>) {
        val c = evt.getSource()
        eventPublisher!!.publishEvent(AddContactToListEvent(c.getUserId(), c.getContributionPeriodId()))
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onUpdate(evt: PostUpdateEvent<Contribution>) {
        val c = evt.getSource()
        eventPublisher!!.publishEvent(AddContactToListEvent(c.getUserId(), c.getContributionPeriodId()))
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onDelete(evt: PostRemoveEvent<Contribution>) {
        val c = evt.getSource()
        eventPublisher!!.publishEvent(RemoveContactFromListEvent(c.getUserId(), c.getContributionPeriodId()))
    }
}
