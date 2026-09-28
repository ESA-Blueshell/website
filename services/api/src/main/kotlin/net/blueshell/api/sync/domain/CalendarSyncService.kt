package net.blueshell.api.sync.domain

import net.blueshell.api.event.api.CalendarAdapter
import net.blueshell.api.event.api.CalendarEventData
import net.blueshell.api.event.api.EventService
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.sync.api.ExternalIdMappingService
import net.blueshell.api.sync.api.ExternalIdMappingService.Companion.EVENT_AGGREGATE
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Puts an approved event on the calendar, keeps it current and takes it off once it is unapproved
 * or deleted, recording its id there in `external_id_mapping`.
 */
@Service
class CalendarSyncService(
    private val calendar: CalendarAdapter,
    private val mappings: ExternalIdMappingService,
    private val events: EventService,
) {
    /** Answers why nothing was pushed, or null where it was. */
    @Transactional
    fun sync(eventId: Long): String? {
        val event = events.findByIdIncludingDeletedOrNull(eventId) ?: return "The event no longer exists."
        val isSoftDeleted = event.deletedAt?.isBefore(ACTIVE_ROW_THRESHOLD) == true
        val current = mappings.find(EVENT_AGGREGATE, eventId, SYSTEM)?.externalId
        val next =
            when {
                !event.approved || isSoftDeleted -> {
                    current?.let { calendar.removeEvent(eventId, it) }
                    null
                }
                else -> {
                    val data =
                        CalendarEventData(
                            title = event.title,
                            location = event.location,
                            description = event.description,
                            startTime = event.startTime,
                            endTime = event.endTime,
                        )
                    current?.also { calendar.updateEvent(eventId, it, data) } ?: calendar.addEvent(eventId, data).externalId
                }
            }
        mappings.upsert(EVENT_AGGREGATE, eventId, SYSTEM, next)
        return null
    }

    companion object {
        private val SYSTEM = TargetSystem.GOOGLE_CALENDAR.name
        private val ACTIVE_ROW_THRESHOLD: Instant = Instant.parse("9999-01-01T00:00:00Z")
    }
}
