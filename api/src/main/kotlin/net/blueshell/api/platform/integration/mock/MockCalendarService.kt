package net.blueshell.api.platform.integration.mock

import net.blueshell.api.event.persistence.Event
import net.blueshell.api.platform.integration.calendar.CalendarService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.io.IOException
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.stream.Collectors

/**
 * Test double for CalendarService: in-memory store with stable IDs.
 */
@Service
@Primary
@Profile("test | dev")
class MockCalendarService : CalendarService() {
    private val seq = AtomicLong(1000000L)
    private val eventsById: MutableMap<String, Event> = ConcurrentHashMap<String, Event>()

    val readOnlyEvents: MutableMap<String, Event> = Collections.unmodifiableMap(eventsById)

    @Throws(IOException::class)
    override fun add(event: Event) {
        if (event.googleId != null) {
            update(event)
            return
        }
        val id = "mock-" + seq.andIncrement
        val stored: Event = copyOf(event)
        stored.googleId = id
        eventsById[id] = stored
        event.googleId = id
        log.info(
            "[calendar-mock] added event id={} title='{}' start={} end={}",
            id, event.title, event.startTime, event.endTime
        )
    }

    @Throws(IOException::class)
    override fun update(event: Event) {
        if (event.googleId == null) {
            add(event)
            return
        }
        val id = event.googleId
        if (id != null) {
            eventsById[id] = copyOf(event)
            log.info("[calendar-mock] updated event id={} title='{}'", id, event.title)
        }
    }

    @Throws(IOException::class)
    override fun remove(event: Event) {
        val id = event.googleId ?: return
        eventsById.remove(id)
        event.googleId = null
        log.info("[calendar-mock] removed event id={}", id)
    }

    @Throws(IOException::class)
    override fun sync(event: Event) {
        if (event.googleId == null) add(event)
        else update(event)
    }

    fun clear() {
        eventsById.clear()
    }

    fun findByGoogleId(googleId: String): Event? {
        return eventsById[googleId]
    }

    fun findBetween(startInclusive: Instant, endExclusive: Instant): MutableMap<String, Event> {
        return eventsById.entries.stream()
            .filter { en: MutableMap.MutableEntry<String, Event> ->
                val s = en.value.startTime
                val e = en.value.endTime
                !s.isBefore(startInclusive) && e.isBefore(endExclusive)
            }
            .collect(Collectors.toUnmodifiableMap({ entry -> entry.key }, { entry -> entry.value }))
    }

    companion object {
        private val log = LoggerFactory.getLogger(MockCalendarService::class.java)

        private fun copyOf(src: Event): Event {
            val e = Event()
            e.title = src.title
            e.location = src.location
            e.description = src.description
            e.startTime = src.startTime
            e.endTime = src.endTime
            e.googleId = src.googleId
            return e
        }
    }
}
