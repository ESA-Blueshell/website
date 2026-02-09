package net.blueshell.api.committee.application.listener

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.event.jpa.PostPersistEvent
import net.blueshell.api.shared.event.jpa.PostRemoveEvent
import net.blueshell.api.shared.event.jpa.PostUpdateEvent
import net.blueshell.api.committee.domain.model.CommitteeMember
import net.blueshell.api.user.application.UserService
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
