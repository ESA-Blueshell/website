package net.blueshell.api.platform.integration.calendar.job

import net.blueshell.api.platform.integration.calendar.CalendarService
import net.blueshell.api.event.service.EventService
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
class SyncEventToCalendarJob(
    private val calendarService: CalendarService,
    private val eventService: EventService
) {
    @Async
    @Retryable(
        retryFor = [Exception::class, UncheckedIOException::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 2000, multiplier = 2.0)
    )
    fun sync(eventId: Long): CompletableFuture<Void?> {
        val key = key("sync", eventId)
        if (processing.putIfAbsent(key, true) != null) return CompletableFuture.completedFuture<Void?>(null)

        val lock: ReentrantLock = locks.computeIfAbsent(eventId) { k: Long? -> ReentrantLock() }
        try {
            lock.lock()
            val e = eventService.findById(eventId)
            if (!e.approved) {
                // remove if previously on Google
                if (e.googleId != null) {
                    calendarService.remove(e)
                    e.googleId = null
                    eventService.update(e)
                    log.info("EventId {} unapproved -> removed from Google", eventId)
                } else {
                    log.info("EventId {} unapproved and not on Google -> noop", eventId)
                }
                return CompletableFuture.completedFuture<Void?>(null)
            }

            // approved: add or update
            if (e.googleId == null) {
                calendarService.add(e)
                eventService.update(e)
                log.info("EventId {} synced by add; googleId={}", eventId, e.googleId)
            } else {
                calendarService.update(e)
                log.info("EventId {} synced by update; googleId={}", eventId, e.googleId)
            }
            return CompletableFuture.completedFuture<Void?>(null)
        } catch (ex: IOException) {
            throw UncheckedIOException(ex)
        } finally {
            lock.unlock()
            processing.remove(key)
        }
    }

    @Recover
    fun recover(ex: Exception, eventId: Long?): CompletableFuture<Void?> {
        processing.remove(key("sync", eventId))
        log.error("Giving up sync for eventId {}: {}", eventId, ex.message, ex)
        return CompletableFuture.completedFuture<Void?>(null)
    }

    private fun key(op: String?, id: Long?): String {
        return op + "_" + id + "_" + (System.currentTimeMillis() / 10000)
    }

    companion object {
        private val log = LoggerFactory.getLogger(SyncEventToCalendarJob::class.java)
        private val locks = ConcurrentHashMap<Long, ReentrantLock>()
        private val processing = ConcurrentHashMap<String, Boolean>()
    }
}
