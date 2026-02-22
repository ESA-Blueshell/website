package net.blueshell.api.platform.integration.calendar.job

import com.fasterxml.jackson.databind.ObjectMapper
import net.blueshell.api.domain.event.application.EventService
import net.blueshell.api.domain.event.application.calendar.CalendarAdapter
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.shared.job.CalendarEventRef
import net.blueshell.api.shared.job.CalendarJobs
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Job handler for removing events from external calendar.
 *
 * Uses CalendarAdapter (ADR-019 ACL) to isolate from specific calendar provider.
 */
@Component
class RemoveEventFromCalendarJob(
    objectMapper: ObjectMapper,
    private val calendarAdapter: CalendarAdapter,
    private val events: EventService
) : AbstractJsonJobHandler<CalendarEventRef>(objectMapper, CalendarJobs.RemoveEvent.payloadType) {
    override val jobType: String = CalendarJobs.RemoveEvent.type

    override fun handlePayload(payload: CalendarEventRef) {
        val event = events.findByIdIncludingDeletedOrNull(payload.eventId)
        if (event == null) {
            log.warn("Skipping calendar removal; event not found (including deleted). eventId={}", payload.eventId)
            return
        }
        if (event.googleId == null) {
            log.debug(
                "Skipping calendar removal; no googleId present. eventId={} deletedAt={}",
                event.id,
                event.deletedAt
            )
            return
        }

        val googleId = event.googleId!!

        // Use adapter to remove event from external calendar
        calendarAdapter.removeEvent(event.id!!, googleId)
        if (isDeleted(event.deletedAt)) {
            log.info(
                "Removed calendar event due to domain event deletion. eventId={} googleId={}",
                event.id,
                googleId
            )
            return
        }

        events.updateCalendarLink(event, null)
        log.info("Removed calendar event and cleared googleId. eventId={} googleId={}", event.id, googleId)
    }

    private fun isDeleted(deletedAt: Instant?): Boolean {
        return deletedAt != null && deletedAt.isBefore(ACTIVE_ROW_THRESHOLD)
    }

    companion object {
        private val log = LoggerFactory.getLogger(RemoveEventFromCalendarJob::class.java)
        private val ACTIVE_ROW_THRESHOLD: Instant = Instant.parse("9999-01-01T00:00:00Z")
    }
}
