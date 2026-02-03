package net.blueshell.api.service.mock

import net.blueshell.api.model.event.Event
import net.blueshell.api.service.CalendarService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.io.IOException
import java.time.Instant
import java.util.*
import java.util.Map
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Function
import java.util.stream.Collectors
import kotlin.collections.MutableMap
import kotlin.collections.remove

/**
 * Test double for CalendarService: in-memory store with stable IDs.
 */
@Service
@Primary
@Profile("test | dev")
class MockCalendarService : CalendarService() {
    private val seq = AtomicLong(1000000L)
    private val eventsById: MutableMap<String?, Event?> = ConcurrentHashMap<String?, Event?>()

    val readOnlyEvents: MutableMap<String?, Event?> = Collections.unmodifiableMap<String?, Event?>(eventsById)

    @Throws(IOException::class)
    override fun add(event: Event) {
        if (event.getGoogleId() != null) {
            update(event)
            return
        }
        val id = "mock-" + seq.getAndIncrement()
        val stored: Event = copyOf(event)
        stored.setGoogleId(id)
        eventsById.put(id, stored)
        event.setGoogleId(id)
        MockCalendarService.log.info(
            "[calendar-mock] added event id={} title='{}' start={} end={}",
            id, event.getTitle(), event.getStartTime(), event.getEndTime()
        )
    }

    @Throws(IOException::class)
    override fun update(event: Event) {
        if (event.getGoogleId() == null) {
            add(event)
            return
        }
        val id = event.getGoogleId()
        eventsById.put(id, copyOf(event))
        MockCalendarService.log.info("[calendar-mock] updated event id={} title='{}'", id, event.getTitle())
    }

    @Throws(IOException::class)
    override fun remove(event: Event) {
        val id = event.getGoogleId()
        if (id == null) return
        eventsById.remove(id)
        event.setGoogleId(null)
        MockCalendarService.log.info("[calendar-mock] removed event id={}", id)
    }

    @Throws(IOException::class)
    override fun sync(event: Event) {
        if (event.getGoogleId() == null) add(event)
        else update(event)
    }

    fun clear() {
        eventsById.clear()
    }

    fun findByGoogleId(googleId: String?): Event? {
        val e = eventsById.get(googleId)
        return if (e == null) null else copyOf(e)
    }

    fun findBetween(startInclusive: Instant, endExclusive: Instant): MutableMap<String?, Event?> {
        return eventsById.entries.stream()
            .filter { en: MutableMap.MutableEntry<String?, Event?>? ->
                val s = en!!.value!!.getStartTime()
                val e = en.value!!.getEndTime()
                s != null && e != null && !s.isBefore(startInclusive) && e.isBefore(endExclusive)
            }
            .collect(Collectors.toUnmodifiableMap(Function { Map.Entry.key }, Function { Map.Entry.value }))
    }

    companion object {
        private val log = LoggerFactory.getLogger(MockCalendarService::class.java)

        private fun copyOf(src: Event): Event {
            val e = Event()
            e.setId(src.getId())
            e.setTitle(src.getTitle())
            e.setLocation(src.getLocation())
            e.setDescription(src.getDescription())
            e.setStartTime(src.getStartTime())
            e.setEndTime(src.getEndTime())
            e.setGoogleId(src.getGoogleId())
            return e
        }
    }
}
