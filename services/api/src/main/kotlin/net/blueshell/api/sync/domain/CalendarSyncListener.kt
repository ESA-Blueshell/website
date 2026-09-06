package net.blueshell.api.sync.domain

import net.blueshell.api.event.api.EventChanged
import net.blueshell.api.shared.job.CalendarJobs
import net.blueshell.api.shared.job.JobQueue
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Modulith listener that fans event-changed out as a queued per-event
 * calendar sync job. The Google Calendar HTTP push runs inside the
 * resulting [CalendarJobs.SyncCalendarEvent] handler with its own
 * retry schedule, so upstream blips don't propagate into the event's
 * own transaction.
 */
@Component
class CalendarSyncListener(
    private val jobs: JobQueue,
) {
    @ApplicationModuleListener
    fun on(event: EventChanged) {
        jobs.runAsync(CalendarJobs.SyncCalendarEvent, CalendarJobs.SyncCalendarEventPayload(event.eventId))
    }
}
