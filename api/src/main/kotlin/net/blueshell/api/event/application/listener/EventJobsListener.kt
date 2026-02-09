package net.blueshell.api.event.application.listener

import net.blueshell.api.event.application.EventService
import net.blueshell.api.event.application.EventSignUpService
import net.blueshell.api.event.application.event.EventChange
import net.blueshell.api.event.application.event.EventChanged
import net.blueshell.api.event.application.event.EventSignUpCreated
import net.blueshell.api.platform.integration.calendar.job.CalendarEventRef
import net.blueshell.api.platform.integration.queue.CalendarJobs
import net.blueshell.api.platform.integration.queue.EmailJobs
import net.blueshell.api.platform.integration.queue.JobDispatcher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class EventJobsListener(
    private val jobDispatcher: JobDispatcher,
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
                    jobDispatcher.enqueue(
                        CalendarJobs.AddEvent,
                        CalendarEventRef(e.id!!)
                    )
                }
            }
            EventChange.UPDATED -> {
                val e = events.findById(evt.eventId)
                if (e.approved) {
                    jobDispatcher.enqueue(
                        CalendarJobs.SyncEvent,
                        CalendarEventRef(e.id!!)
                    )
                } else {
                    jobDispatcher.enqueue(
                        CalendarJobs.RemoveEvent,
                        CalendarEventRef(e.id!!)
                    )
                }
            }
            EventChange.DELETED -> jobDispatcher.enqueue(
                CalendarJobs.RemoveEvent,
                CalendarEventRef(evt.eventId)
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
            jobDispatcher.enqueue(
                EmailJobs.EventSignup,
                EmailJobs.EventSignupPayload(e.id!!)
            )
        }
    }
}
