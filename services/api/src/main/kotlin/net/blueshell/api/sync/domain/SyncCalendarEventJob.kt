package net.blueshell.api.sync.domain

import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.shared.job.CalendarJobs
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Per-event calendar sync: pushes one event's current state to every
 * registered calendar target (currently Google Calendar). Enqueued by
 * [CalendarSyncListener] in response to [EventChanged] so the HTTP push
 * runs in the job queue with retry isolation instead of inline inside
 * the listener's transaction.
 */
@Component
class SyncCalendarEventJob(
    objectMapper: ObjectMapper,
    private val calendarSync: CalendarSyncService,
) : AbstractJsonJobHandler<CalendarJobs.SyncCalendarEventPayload>(
    objectMapper,
    CalendarJobs.SyncCalendarEvent.payloadType,
) {
    override val jobType: String = CalendarJobs.SyncCalendarEvent.type

    override fun handlePayload(payload: CalendarJobs.SyncCalendarEventPayload) {
        calendarSync.sync(payload.eventId)
    }
}
