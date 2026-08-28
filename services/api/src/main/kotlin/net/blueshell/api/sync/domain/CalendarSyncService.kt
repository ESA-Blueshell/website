package net.blueshell.api.sync.domain

import net.blueshell.api.event.api.EventService
import net.blueshell.api.event.api.CalendarEventData
import net.blueshell.api.shared.enums.TargetSystem
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Drives calendar sync to every registered calendar target.
 *
 * `data == null` (unapproved or soft-deleted) signals removal. Bridges to
 * `Event.googleId` until that column is dropped.
 */
@Service
class CalendarSyncService(
    private val registry: SyncTargetRegistry,
    private val fanOut: SyncFanOut,
    private val events: EventService,
) {
    @Transactional
    fun sync(eventId: Long) {
        val event = events.findByIdIncludingDeletedOrNull(eventId)
        if (event == null) {
            log.warn("Calendar sync skipped: event {} not found (hard-deleted)", eventId)
            return
        }
        val isSoftDeleted = event.deletedAt?.isBefore(ACTIVE_ROW_THRESHOLD) == true
        val data = if (event.approved && !isSoftDeleted) {
            CalendarEventData(
                title = event.title,
                location = event.location,
                description = event.description,
                startTime = event.startTime,
                endTime = event.endTime,
                approved = true,
            )
        } else null

        fanOut.push(AGGREGATE, eventId, data, registry.forCalendar()) { system, externalId ->
            if (system == TargetSystem.GOOGLE_CALENDAR && !isSoftDeleted) {
                events.updateCalendarLink(event, externalId)
            }
        }
    }

    companion object {
        private const val AGGREGATE = "EVENT"
        private val ACTIVE_ROW_THRESHOLD: Instant = Instant.parse("9999-01-01T00:00:00Z")
        private val log = LoggerFactory.getLogger(CalendarSyncService::class.java)
    }
}
