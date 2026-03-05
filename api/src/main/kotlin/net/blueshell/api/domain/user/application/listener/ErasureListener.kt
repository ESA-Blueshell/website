package net.blueshell.api.domain.user.application.listener

import net.blueshell.api.domain.user.application.event.UserDeleted
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class ErasureListener(
    private val jobs: TrackedJobDispatcher
) {
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onDeleted(evt: UserDeleted) {
        jobs.enqueueFromActor(
            ContactJobs.DeleteContact,
            ContactJobs.DeleteContactPayload(userId = evt.userId),
            evt
        )
    }
}
