package net.blueshell.api.event.domain

import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/** Sends the guest signup email after commit. Calendar sync is driven by [CalendarSyncListener]. */
@Component
class EventJobsListener(
    private val jobs: TrackedJobDispatcher,
    private val signUps: EventSignUpService,
) {
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onPersist(evt: EventSignUpCreated) {
        val guestAccessToken = evt.guestAccessToken ?: return
        val e = signUps.findById(evt.signUpId)
        if (e.guest != null) {
            jobs.runAsyncFromActor(
                EmailJobs.EventSignup,
                EmailJobs.EventSignupPayload(e.id!!, guestAccessToken),
                evt,
            )
        }
    }
}
