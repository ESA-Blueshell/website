package net.blueshell.api.event.domain

import net.blueshell.api.event.api.CalendarAdapter
import net.blueshell.api.event.api.CalendarEventData
import net.blueshell.api.event.api.CalendarEventRef
import net.blueshell.api.shared.credentials.Credentials
import net.blueshell.api.shared.credentials.WhenCredentialsMissing
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory [CalendarAdapter], standing in for Google Calendar where its calendar id or key is
 * not set: stable ids, events held in a map, and inspection methods for a test to assert against.
 */
@Service
@WhenCredentialsMissing(Credentials.GOOGLE_CALENDAR_ID, Credentials.GOOGLE_CALENDAR_KEY)
class MockCalendarAdapter : CalendarAdapter {
    private val seq = AtomicLong(1000000L)
    private val eventsById: MutableMap<String, StoredEvent> = ConcurrentHashMap()

    override fun addEvent(
        eventId: Long,
        eventData: CalendarEventData,
    ): CalendarEventRef {
        val mockId = "mock-${seq.incrementAndGet()}"
        val stored =
            StoredEvent(
                eventId = eventId,
                externalId = mockId,
                title = eventData.title,
                location = eventData.location,
                description = eventData.description,
                startTime = eventData.startTime,
                endTime = eventData.endTime,
            )
        eventsById[mockId] = stored

        log.info(
            "[mock-calendar] Added event eventId={} externalId={} title='{}'",
            eventId,
            mockId,
            eventData.title,
        )

        return CalendarEventRef(
            externalId = mockId,
            externalUrl = "https://mock-calendar.example.com/event/$mockId",
        )
    }

    override fun updateEvent(
        eventId: Long,
        externalId: String,
        eventData: CalendarEventData,
    ) {
        val stored = eventsById[externalId]
        if (stored != null) {
            eventsById[externalId] =
                stored.copy(
                    title = eventData.title,
                    location = eventData.location,
                    description = eventData.description,
                    startTime = eventData.startTime,
                    endTime = eventData.endTime,
                )
            log.info(
                "[mock-calendar] Updated event eventId={} externalId={} title='{}'",
                eventId,
                externalId,
                eventData.title,
            )
        } else {
            log.error(
                "[mock-calendar] Cannot update missing event eventId={} externalId={}",
                eventId,
                externalId,
            )
            throw IllegalStateException(
                "[mock-calendar] Cannot update missing event eventId=$eventId externalId=$externalId",
            )
        }
    }

    override fun removeEvent(
        eventId: Long,
        externalId: String,
    ) {
        val removed = eventsById.remove(externalId)
        if (removed != null) {
            log.info("[mock-calendar] Removed event eventId={} externalId={}", eventId, externalId)
        } else {
            log.error(
                "[mock-calendar] Cannot remove missing event eventId={} externalId={}",
                eventId,
                externalId,
            )
            throw IllegalStateException(
                "[mock-calendar] Cannot remove missing event eventId=$eventId externalId=$externalId",
            )
        }
    }

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
    fun findByExternalId(externalId: String): StoredEvent? = eventsById[externalId]

    /**
     * Get all stored events.
     */
    fun getAllEvents(): Map<String, StoredEvent> = eventsById.toMap()

    /**
     * Get the count of stored events.
     */
    fun getEventCount(): Int = eventsById.size

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
)
