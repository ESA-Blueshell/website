package net.blueshell.api.user.application.listener

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.platform.integration.contact.job.SyncContactJobHandler
import net.blueshell.api.platform.integration.contact.job.SyncContactPayload
import net.blueshell.api.platform.integration.queue.JobDispatcher
import net.blueshell.api.user.application.event.UserCreatedEvent
import net.blueshell.api.user.application.event.UserUpdatedEvent
import net.blueshell.api.committee.application.CommitteeMemberService
import net.blueshell.api.user.application.UserService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class UserEventListener(
    private val jobDispatcher: JobDispatcher,
    private val committeeMembers: CommitteeMemberService,
    private val users: UserService
) {
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onCreate(evt: UserCreatedEvent) {
        jobDispatcher.enqueue(SyncContactJobHandler.JOB_TYPE, SyncContactPayload(evt.userId))
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onUpdate(evt: UserUpdatedEvent) {
        val u = users.findById(evt.userId)
        jobDispatcher.enqueue(SyncContactJobHandler.JOB_TYPE, SyncContactPayload(evt.userId))
        if (!u.hasRole(Role.MEMBER)) {
            u.committeeMembers.forEach { committeeMembers.delete(it) }
        }
    }
}
