package net.blueshell.api.membership.application.listener

import net.blueshell.api.membership.application.event.MembershipChangeType
import net.blueshell.api.membership.application.event.MembershipChangedEvent
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.user.application.UserService
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class MembershipEventListener(
    private val users: UserService
) {
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onChange(evt: MembershipChangedEvent) {
        when (evt.changeType) {
            MembershipChangeType.CREATED,
            MembershipChangeType.UPDATED -> {
                if (evt.active) {
                    log.info("Updating membership for user {} adding role {}", evt.userId, Role.MEMBER)
                    users.addRole(evt.userId, Role.MEMBER)
                } else {
                    log.info("Updating membership for user {} removing role {}", evt.userId, Role.MEMBER)
                    users.removeRole(evt.userId, Role.MEMBER)
                }
            }
            MembershipChangeType.DELETED -> {
                log.info("Deleting membership for user {} removing role {}", evt.userId, Role.MEMBER)
                users.removeRole(evt.userId, Role.MEMBER)
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(MembershipEventListener::class.java)
    }
}
