package net.blueshell.api.platform.integration.sync.application

import net.blueshell.api.domain.event.application.EventService
import net.blueshell.api.domain.event.application.calendar.CalendarEventData
import net.blueshell.api.platform.integration.sync.port.SyncTargetRegistry
import net.blueshell.api.platform.integration.sync.port.TargetSystem
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Drives calendar sync to every registered [CalendarSyncTarget].
 *
 * `data == null` (unapproved or soft-deleted) signals removal. Bridges to
 * `Event.googleId` until that column is dropped.
 */
@Service
class CalendarSyncService(
    private val registry: SyncTargetRegistry,
    private val mappings: ExternalIdMappingService,
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
        val present = event.approved && !isSoftDeleted
        val data = if (present) CalendarEventData(
            title = event.title,
            location = event.location,
            description = event.description,
            startTime = event.startTime,
            endTime = event.endTime,
            approved = true,
        ) else null

        registry.forCalendar().forEach { target ->
            val current = mappings.find(AGGREGATE, eventId, target.system.name)?.externalId
            val newId = target.push(eventId, data, current)
            mappings.upsert(AGGREGATE, eventId, target.system.name, newId)
            if (target.system == TargetSystem.GOOGLE_CALENDAR && !isSoftDeleted) {
                events.updateCalendarLink(event, newId)
            }
        }
    }

    companion object {
        private const val AGGREGATE = "EVENT"
        private val ACTIVE_ROW_THRESHOLD: Instant = Instant.parse("9999-01-01T00:00:00Z")
        private val log = LoggerFactory.getLogger(CalendarSyncService::class.java)
    }
}
