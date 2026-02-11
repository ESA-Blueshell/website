package net.blueshell.api.domain.user.application.listener

import net.blueshell.api.domain.committee.application.CommitteeMemberService
import net.blueshell.api.platform.integration.queue.ContactJobs
import net.blueshell.api.platform.integration.queue.JobDispatcher
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.user.application.UserService
import net.blueshell.api.user.application.event.UserCreated
import net.blueshell.api.user.application.event.UserUpdated
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
        jobDispatcher.enqueue(
            ContactJobs.SyncContact,
            ContactJobs.SyncContactPayload(evt.userId)
        )
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onUpdate(evt: UserUpdated) {
        val u = users.findById(evt.userId)
        jobDispatcher.enqueue(
            ContactJobs.SyncContact,
            ContactJobs.SyncContactPayload(evt.userId)
        )
        if (!u.hasRole(Role.MEMBER)) {
            u.committeeMembers.forEach { committeeMembers.delete(it) }
        }
    }
}
