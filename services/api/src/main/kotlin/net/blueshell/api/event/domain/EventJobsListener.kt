package net.blueshell.api.event.domain

import net.blueshell.api.shared.event.AfterCommitListener
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.shared.job.runAsyncFromActor
import org.springframework.stereotype.Component

/** Sends the guest signup email after commit. Calendar sync is driven by [CalendarSyncListener]. */
@Component
class EventJobsListener(
    private val jobs: JobQueue,
    private val signUps: EventSignUpService,
) {
    @AfterCommitListener
    fun onPersist(evt: EventSignUpCreated) {
        val guestAccessToken = evt.guestAccessToken ?: return
        val e = signUps.findById(evt.signUpId)
        if (e.guest != null) {
            jobs.runAsyncFromActor(
                EventJobs.EventSignup,
                EventJobs.EventSignupPayload(e.id!!, guestAccessToken),
                JobTrigger.SITE_ACTION,
                evt,
            )
        }
    }
}
