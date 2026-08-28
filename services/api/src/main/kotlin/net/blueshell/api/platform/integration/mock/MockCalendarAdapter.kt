package net.blueshell.api.platform.integration.mock

import net.blueshell.api.event.api.CalendarAdapter
import net.blueshell.api.event.domain.CalendarEventData
import net.blueshell.api.event.domain.CalendarEventRef
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import net.blueshell.api.event.api.CalendarEventData
import net.blueshell.api.event.api.CalendarEventRef

/**
 * Mock implementation of CalendarAdapter for testing and development.
 *
 * This implementation provides an in-memory calendar that:
 * - Generates stable mock IDs
 * - Stores events in memory
 * - Provides inspection methods for testing
 *
 * Active in 'test' and 'dev' profiles.
 */
@Service
@Primary
@Profile("test | dev")
class MockCalendarAdapter : CalendarAdapter {
    private val seq = AtomicLong(1000000L)
    private val eventsById: MutableMap<String, StoredEvent> = ConcurrentHashMap()

    override fun addEvent(eventId: Long, eventData: CalendarEventData): CalendarEventRef {
        val mockId = "mock-${seq.incrementAndGet()}"
        val stored = StoredEvent(
            eventId = eventId,
            externalId = mockId,
            title = eventData.title,
            location = eventData.location,
            description = eventData.description,
            startTime = eventData.startTime,
            endTime = eventData.endTime,
            approved = eventData.approved
        )
        eventsById[mockId] = stored

        log.info(
            "[mock-calendar] Added event eventId={} externalId={} title='{}'",
            eventId, mockId, eventData.title
        )

        return CalendarEventRef(
            externalId = mockId,
            externalUrl = "https://mock-calendar.example.com/event/$mockId"
        )
    }

    override fun updateEvent(eventId: Long, externalId: String, eventData: CalendarEventData) {
        val stored = eventsById[externalId]
        if (stored != null) {
            eventsById[externalId] = stored.copy(
                title = eventData.title,
                location = eventData.location,
                description = eventData.description,
                startTime = eventData.startTime,
                endTime = eventData.endTime,
                approved = eventData.approved
            )
            log.info(
                "[mock-calendar] Updated event eventId={} externalId={} title='{}'",
                eventId, externalId, eventData.title
            )
        } else {
            log.error(
                "[mock-calendar] Cannot update missing event eventId={} externalId={}",
                eventId, externalId
            )
            throw IllegalStateException(
                "[mock-calendar] Cannot update missing event eventId=$eventId externalId=$externalId"
            )
        }
    }

    override fun removeEvent(eventId: Long, externalId: String) {
        val removed = eventsById.remove(externalId)
        if (removed != null) {
            log.info("[mock-calendar] Removed event eventId={} externalId={}", eventId, externalId)
        } else {
            log.error(
                "[mock-calendar] Cannot remove missing event eventId={} externalId={}",
                eventId, externalId
            )
            throw IllegalStateException(
                "[mock-calendar] Cannot remove missing event eventId=$eventId externalId=$externalId"
            )
        }
    }

    override fun syncEvent(eventId: Long, eventData: CalendarEventData, externalId: String?): CalendarEventRef? {
        return when {
            // Event has external ID and is approved -> update
            externalId != null && eventData.approved -> {
                updateEvent(eventId, externalId, eventData)
                CalendarEventRef(externalId, "https://mock-calendar.example.com/event/$externalId")
            }

            // Event has external ID but not approved -> remove
            externalId != null && !eventData.approved -> {
                removeEvent(eventId, externalId)
                null
            }

            // Event has no external ID and is approved -> add
            externalId == null && eventData.approved -> {
                addEvent(eventId, eventData)
            }

            // Event has no external ID and not approved -> nothing to do
            else -> {
                log.debug("[mock-calendar] Event eventId={} has no external ID and is not approved, skipping sync", eventId)
                null
            }
        }
    }

    // ========== Testing utilities ==========

    /**
     * Clear all stored events. Useful for test cleanup.
     */
    fun clear() {
        eventsById.clear()
        log.info("[mock-calendar] Cleared all events")
    }

    /**
     * Find an event by its external (mock) ID.
     */
    fun findByExternalId(externalId: String): StoredEvent? {
        return eventsById[externalId]
    }

    /**
     * Get all stored events.
     */
    fun getAllEvents(): Map<String, StoredEvent> {
        return eventsById.toMap()
    }

    /**
     * Get the count of stored events.
     */
    fun getEventCount(): Int {
        return eventsById.size
    }

    companion object {
        private val log = LoggerFactory.getLogger(MockCalendarAdapter::class.java)
    }
}

/**
 * Internal representation of a stored calendar event in the mock.
 */
data class StoredEvent(
    val eventId: Long,
    val externalId: String,
    val title: String,
    val location: String?,
    val description: String?,
    val startTime: java.time.Instant,
    val endTime: java.time.Instant,
    val approved: Boolean
)
