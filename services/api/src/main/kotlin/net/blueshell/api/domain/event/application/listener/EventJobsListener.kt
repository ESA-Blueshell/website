package net.blueshell.api.domain.event.application.listener

import net.blueshell.api.domain.event.application.EventSignUpService
import net.blueshell.api.domain.event.application.event.EventChanged
import net.blueshell.api.domain.event.application.event.EventSignUpCreated
import net.blueshell.api.shared.job.CalendarEventRef
import net.blueshell.api.shared.job.CalendarJobs
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class EventJobsListener(
    private val jobs: TrackedJobDispatcher,
    private val signUps: EventSignUpService
) {
    /**
     * After commit, enqueue calendar sync for any event change.
     * The SyncEventToCalendarJob handles all cases (add/update/remove)
     * based on the event's current approval status and soft-deletion state.
     */
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onChange(evt: EventChanged) {
        jobs.enqueueFromActor(
            CalendarJobs.SyncEvent,
            CalendarEventRef(evt.eventId),
            evt
        )
    }

    /**
     * Send e-mail only if the transaction COMMITTED successfully.
     */
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onPersist(evt: EventSignUpCreated) {
        val guestAccessToken = evt.guestAccessToken ?: return
        val e = signUps.findById(evt.signUpId)
        if (e.guest != null) {
            jobs.enqueueFromActor(
                EmailJobs.EventSignup,
                EmailJobs.EventSignupPayload(e.id!!, guestAccessToken),
                evt
            )
        }
    }
}
