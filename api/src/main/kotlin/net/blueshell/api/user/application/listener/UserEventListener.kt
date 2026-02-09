package net.blueshell.api.user.application.listener

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.platform.integration.event.job.SyncContactEvent
import net.blueshell.api.user.application.event.UserCreatedEvent
import net.blueshell.api.user.application.event.UserUpdatedEvent
import net.blueshell.api.committee.application.CommitteeMemberService
import net.blueshell.api.user.application.UserService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class UserEventListener(
    private val eventPublisher: ApplicationEventPublisher,
    private val committeeMembers: CommitteeMemberService,
    private val users: UserService
) {
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onCreate(evt: UserCreatedEvent) {
        eventPublisher.publishEvent(SyncContactEvent(evt.userId))
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onUpdate(evt: UserUpdatedEvent) {
        val u = users.findById(evt.userId)
        eventPublisher.publishEvent(SyncContactEvent(evt.userId))
        if (!u.hasRole(Role.MEMBER)) {
            u.committeeMembers.forEach { committeeMembers.delete(it) }
        }
    }
}
