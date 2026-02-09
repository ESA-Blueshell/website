package net.blueshell.api.membership.application.listener

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.event.jpa.PostRemoveEvent
import net.blueshell.api.shared.event.jpa.PostUpdateEvent
import net.blueshell.api.shared.event.jpa.PrePersistEvent
import net.blueshell.api.membership.persistence.Membership
import net.blueshell.api.user.application.UserService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class MembershipEventListener(
    private val users: UserService
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onCreate(evt: PrePersistEvent<Membership>) {
        val m = evt.source
        log.info("Creating membership for user {} adding role {}", m.userId, Role.MEMBER)
        users.addRole(m.userId, Role.MEMBER)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onUpdate(evt: PostUpdateEvent<Membership>) {
        val m = evt.source
        if (m.endDate == null) {
            log.info(
                "Updating membership for user {} adding role {}",
                m.userId,
                Role.MEMBER
            )
            users.addRole(m.userId, Role.MEMBER)
        } else {
            log.info(
                "Updating membership for user {} removing role {}",
                m.userId,
                Role.MEMBER
            )
            users.removeRole(m.userId, Role.MEMBER)
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onDelete(evt: PostRemoveEvent<Membership>) {
        val m = evt.source
        log.info("Deleting membership for user {} removing role {}", m.userId, Role.MEMBER)
        users.removeRole(m.userId, Role.MEMBER)
    }

    companion object {
        private val log = LoggerFactory.getLogger(MembershipEventListener::class.java)
    }
}
