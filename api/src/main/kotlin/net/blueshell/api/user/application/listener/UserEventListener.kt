package net.blueshell.api.user.application.listener

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.platform.integration.event.job.SyncContactEvent
import net.blueshell.api.shared.event.jpa.PostPersistEvent
import net.blueshell.api.shared.event.jpa.PostUpdateEvent
import net.blueshell.api.user.persistence.User
import net.blueshell.api.committee.application.CommitteeMemberService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class UserEventListener(
    private val eventPublisher: ApplicationEventPublisher,
    private val committeeMembers: CommitteeMemberService
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun postPersist(evt: PostPersistEvent<User>) {
        val u = evt.source
        eventPublisher.publishEvent(SyncContactEvent(u.id))
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onUpdate(evt: PostUpdateEvent<User>) {
        val u = evt.source
        eventPublisher.publishEvent(SyncContactEvent(u.id))
        if (!u.hasRole(Role.MEMBER)) {
            u.committeeMembers.forEach { committeeMembers.delete(it) }
        }
    }
}
