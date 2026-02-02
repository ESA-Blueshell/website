package net.blueshell.api.listener.jpa

import lombok.RequiredArgsConstructor
import lombok.extern.slf4j.Slf4j
import net.blueshell.api.common.enums.Role
import net.blueshell.api.common.event.jpa.PostRemoveEvent
import net.blueshell.api.common.event.jpa.PostUpdateEvent
import net.blueshell.api.common.event.jpa.PrePersistEvent
import net.blueshell.api.model.Membership
import net.blueshell.api.service.UserService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Slf4j
@Component
@RequiredArgsConstructor
class MembershipEventListener {
    private val users: UserService? = null

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onCreate(evt: PrePersistEvent<Membership>) {
        val m = evt.getSource()
        MembershipEventListener.log.info("Creating membership for user {} adding role {}", m.getUserId(), Role.MEMBER)
        users!!.addRole(m.getUserId(), Role.MEMBER)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onUpdate(evt: PostUpdateEvent<Membership>) {
        val m = evt.getSource()
        if (m.getEndDate() == null) {
            MembershipEventListener.log.info(
                "Updating membership for user {} adding role {}",
                m.getUserId(),
                Role.MEMBER
            )
            users!!.addRole(m.getUserId(), Role.MEMBER)
        } else {
            MembershipEventListener.log.info(
                "Updating membership for user {} removing role {}",
                m.getUserId(),
                Role.MEMBER
            )
            users!!.removeRole(m.getUserId(), Role.MEMBER)
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onDelete(evt: PostRemoveEvent<Membership>) {
        val m = evt.getSource()
        MembershipEventListener.log.info("Deleting membership for user {} removing role {}", m.getUserId(), Role.MEMBER)
        users!!.removeRole(m.getUserId(), Role.MEMBER)
    }
}
