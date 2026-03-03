package net.blueshell.api.platform.integration.calendar.job

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.domain.event.application.EventService
import net.blueshell.api.domain.event.application.calendar.CalendarAdapter
import net.blueshell.api.domain.event.application.calendar.CalendarEventData
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.shared.job.CalendarEventRef
import net.blueshell.api.shared.job.CalendarJobs
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Unified job handler for all calendar operations (add/update/remove).
 *
 * Uses CalendarAdapter (ADR-019 ACL) to isolate from specific calendar provider.
 * Handles add/update/remove logic based on event approval status and soft-deletion.
 */
@Component
class SyncEventToCalendarJob(
    objectMapper: ObjectMapper,
    private val calendarAdapter: CalendarAdapter,
    private val events: EventService
) : AbstractJsonJobHandler<CalendarEventRef>(objectMapper, CalendarJobs.SyncEvent.payloadType) {
    override val jobType: String = CalendarJobs.SyncEvent.type

    override fun handlePayload(payload: CalendarEventRef) {
        val event = events.findByIdIncludingDeletedOrNull(payload.eventId)
        if (event == null) {
            log.warn("Skipping calendar sync; event not found (hard-deleted). eventId={}", payload.eventId)
            return
        }

        val isSoftDeleted = isDeleted(event.deletedAt)
        val effectiveApproved = event.approved && !isSoftDeleted

        val eventData = CalendarEventData(
            title = event.title,
            location = event.location,
            description = event.description,
            startTime = event.startTime,
            endTime = event.endTime,
            approved = effectiveApproved
        )

        val ref = calendarAdapter.syncEvent(
            eventId = event.id!!,
            eventData = eventData,
            externalId = event.googleId
        )

        if (!isSoftDeleted) {
            events.updateCalendarLink(event, ref?.externalId)
        }
    }

    private fun isDeleted(deletedAt: Instant?): Boolean {
        return deletedAt != null && deletedAt.isBefore(ACTIVE_ROW_THRESHOLD)
    }

    companion object {
        private val log = LoggerFactory.getLogger(SyncEventToCalendarJob::class.java)
        private val ACTIVE_ROW_THRESHOLD: Instant = Instant.parse("9999-01-01T00:00:00Z")
    }
}
