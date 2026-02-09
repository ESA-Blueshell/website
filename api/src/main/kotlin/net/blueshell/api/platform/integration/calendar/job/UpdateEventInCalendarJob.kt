package net.blueshell.api.platform.integration.calendar.job

import net.blueshell.api.platform.integration.calendar.CalendarService
import net.blueshell.api.event.application.EventService
import org.slf4j.LoggerFactory
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Recover
import org.springframework.retry.annotation.Retryable
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.io.IOException
import java.io.UncheckedIOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

@Service
class UpdateEventInCalendarJob(
    private val calendarService: CalendarService,
    private val eventService: EventService
) {
    @Async
    @Retryable(
        retryFor = [Exception::class, UncheckedIOException::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 2000, multiplier = 2.0)
    )
    fun update(eventId: Long): CompletableFuture<Void> {
        val key = key("update", eventId)
        if (processing.putIfAbsent(key, true) != null) return CompletableFuture.completedFuture(null)

        val lock: ReentrantLock = locks.computeIfAbsent(eventId) { k: Long -> ReentrantLock() }
        try {
            lock.lock()
            val e = eventService.findById(eventId)
            if (!e.approved) {
                // reflect visibility change: remove if present
                if (e.googleId != null) {
                    calendarService.remove(e)
                    e.googleId = null
                    eventService.update(e)
                    log.info("EventId {} not approved anymore -> removed from Google", eventId)
                } else {
                    log.info("EventId {} not approved and not on Google -> noop", eventId)
                }
                return CompletableFuture.completedFuture(null)
            }

            if (e.googleId == null) {
                // Not on Google yet: treat as add
                calendarService.add(e)
                eventService.update(e)
                log.info(
                    "EventId {} was missing on Google; added as {}",
                    eventId,
                    e.googleId
                )
            } else {
                calendarService.update(e)
                log.info(
                    "Updated eventId {} on Google (googleId={})",
                    eventId,
                    e.googleId
                )
            }
            return CompletableFuture.completedFuture(null)
        } catch (ex: IOException) {
            throw UncheckedIOException(ex)
        } finally {
            lock.unlock()
            processing.remove(key)
        }
    }

    @Recover
    fun recover(ex: Exception, eventId: Long): CompletableFuture<Void> {
        processing.remove(key("update", eventId))
        log.error("Giving up update for eventId {}: {}", eventId, ex.message, ex)
        return CompletableFuture.completedFuture(null)
    }

    private fun key(op: String, id: Long): String {
        return op + "_" + id + "_" + (System.currentTimeMillis() / 10000)
    }

    companion object {
        private val log = LoggerFactory.getLogger(UpdateEventInCalendarJob::class.java)
        private val locks = ConcurrentHashMap<Long, ReentrantLock>()
        private val processing = ConcurrentHashMap<String, Boolean>()
    }
}
