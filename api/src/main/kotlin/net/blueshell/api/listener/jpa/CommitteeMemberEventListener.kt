package net.blueshell.api.listener.jpa

import net.blueshell.api.common.enums.Role
import net.blueshell.api.common.event.jpa.PostPersistEvent
import net.blueshell.api.common.event.jpa.PostRemoveEvent
import net.blueshell.api.common.event.jpa.PostUpdateEvent
import net.blueshell.api.model.committee.CommitteeMember
import net.blueshell.api.service.UserService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class CommitteeMemberEventListener(
    private val users: UserService
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun postPersist(evt: PostPersistEvent<CommitteeMember>) {
        val c = evt.source
        users.addRole(c.userId, Role.COMMITTEE)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun postUpdate(evt: PostUpdateEvent<CommitteeMember>) {
        val c = evt.source
        users.addRole(c.userId, Role.COMMITTEE)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun postDelete(evt: PostRemoveEvent<CommitteeMember>) {
        val c = evt.source
        val u = users.findById(c.userId)
        if (u.committeeMembers.isEmpty()) {
            users.removeRole(c.userId, Role.COMMITTEE)
        }
    }
}
