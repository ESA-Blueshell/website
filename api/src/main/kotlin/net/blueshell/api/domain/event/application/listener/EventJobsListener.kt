package net.blueshell.api.domain.event.application.listener

import net.blueshell.api.domain.event.application.EventService
import net.blueshell.api.domain.event.application.EventSignUpService
import net.blueshell.api.domain.event.application.event.EventChange
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
    private val events: EventService,
    private val signUps: EventSignUpService
) {
    /**
     * After commit, enqueue add if approved
     */
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onChange(evt: EventChanged) {
        when (evt.changeType) {
            EventChange.CREATED -> {
                val e = events.findById(evt.eventId)
                if (e.approved) {
                    jobs.enqueueFromActor(
                        CalendarJobs.AddEvent,
                        CalendarEventRef(e.id!!),
                        evt
                    )
                }
            }

            EventChange.UPDATED -> {
                val e = events.findById(evt.eventId)
                if (e.approved) {
                    jobs.enqueueFromActor(
                        CalendarJobs.SyncEvent,
                        CalendarEventRef(e.id!!),
                        evt
                    )
                } else {
                    jobs.enqueueFromActor(
                        CalendarJobs.RemoveEvent,
                        CalendarEventRef(e.id!!),
                        evt
                    )
                }
            }

            EventChange.DELETED -> jobs.enqueueFromActor(
                CalendarJobs.RemoveEvent,
                CalendarEventRef(evt.eventId),
                evt
            )
        }
    }

    /**
     * send e-mail only if the transaction COMMITTED successfully
     */
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onPersist(evt: EventSignUpCreated) {
        val e = signUps.findById(evt.signUpId)
        if (e.guest != null) {
            jobs.enqueueFromActor(
                EmailJobs.EventSignup,
                EmailJobs.EventSignupPayload(e.id!!),
                evt
            )
        }
    }
}
