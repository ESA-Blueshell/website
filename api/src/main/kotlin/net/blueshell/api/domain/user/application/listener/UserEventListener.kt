package net.blueshell.api.domain.user.application.listener

import net.blueshell.api.domain.committee.application.CommitteeMemberService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.application.event.UserCreated
import net.blueshell.api.domain.user.application.event.UserUpdated
import net.blueshell.api.platform.integration.contact.adapter.ContactAdapter
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.SyncContactCommand
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class UserEventListener(
    private val jobs: TrackedJobDispatcher,
    private val committeeMembers: CommitteeMemberService,
    private val users: UserService,
    private val contactAdapters: List<ContactAdapter>,
) {
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onCreate(evt: UserCreated) {
        contactAdapters.forEach { adapter ->
            jobs.enqueue(ContactJobs.SyncContactForSystem, SyncContactCommand(evt.userId, adapter.system))
        }
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onUpdate(evt: UserUpdated) {
        val u = users.findById(evt.userId)
        contactAdapters.forEach { adapter ->
            jobs.enqueue(ContactJobs.SyncContactForSystem, SyncContactCommand(evt.userId, adapter.system))
        }
        if (!u.hasRole(Role.MEMBER)) {
            u.committeeMembers.forEach { committeeMembers.delete(it) }
        }
    }
}
