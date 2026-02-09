package net.blueshell.api.user.application.listener

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.platform.integration.contact.job.SyncContactJob
import net.blueshell.api.platform.integration.queue.JobDispatcher
import net.blueshell.api.user.application.event.UserCreated
import net.blueshell.api.user.application.event.UserUpdated
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
    fun onCreate(evt: UserCreated) {
        jobDispatcher.enqueue(SyncContactJob.TYPE, SyncContactJob.Payload(evt.userId))
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onUpdate(evt: UserUpdated) {
        val u = users.findById(evt.userId)
        jobDispatcher.enqueue(SyncContactJob.TYPE, SyncContactJob.Payload(evt.userId))
        if (!u.hasRole(Role.MEMBER)) {
            u.committeeMembers.forEach { committeeMembers.delete(it) }
        }
    }
}
