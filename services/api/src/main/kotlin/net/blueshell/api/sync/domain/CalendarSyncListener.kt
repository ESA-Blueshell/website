package net.blueshell.api.sync.domain

import net.blueshell.api.event.api.EventChanged
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.runAsyncFromActor
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Queues an event's calendar sync with every change to it. The job is written in the change's own
 * transaction, as the Discord triggers are, so a change never commits without it; the Google
 * Calendar push runs in the job, on its own retry schedule.
 */
@Component
class CalendarSyncListener(
    private val jobs: JobQueue,
) {
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    fun on(event: EventChanged) {
        jobs.runAsyncFromActor(
            CalendarJobs.SyncCalendarEvent,
            CalendarJobs.SyncCalendarEventPayload(event.eventId),
            event.changeType.asTrigger(),
            event,
        )
    }
}
