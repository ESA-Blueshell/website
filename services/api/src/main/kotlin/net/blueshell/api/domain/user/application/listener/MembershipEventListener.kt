package net.blueshell.api.domain.user.application.listener

import net.blueshell.api.domain.user.application.event.MembershipChange
import net.blueshell.api.domain.user.application.event.MembershipChanged
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.shared.enums.Role
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
    fun onChange(evt: MembershipChanged) {
        when (evt.changeType) {
            MembershipChange.CREATED,
            MembershipChange.UPDATED -> {
                if (evt.active) {
                    log.info("Updating membership for user {} adding role {}", evt.userId, Role.MEMBER)
                    users.addRole(evt.userId, Role.MEMBER)
                } else {
                    log.info("Updating membership for user {} removing role {}", evt.userId, Role.MEMBER)
                    users.removeRole(evt.userId, Role.MEMBER)
                }
            }
            MembershipChange.DELETED -> {
                log.info("Deleting membership for user {} removing role {}", evt.userId, Role.MEMBER)
                users.removeRole(evt.userId, Role.MEMBER)
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(MembershipEventListener::class.java)
    }
}
