package net.blueshell.api.job.calendar

import net.blueshell.api.service.CalendarService
import net.blueshell.api.service.event.EventService
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
class AddEventToCalendarJob(
    private val calendarService: CalendarService,
    private val eventService: EventService
) {
    @Async
    @Retryable(
        retryFor = [Exception::class, UncheckedIOException::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 2000, multiplier = 2)
    )
    fun add(eventId: Long?): CompletableFuture<Void?> {
        val key = key("add", eventId)
        if (processing.putIfAbsent(key, true) != null) return CompletableFuture.completedFuture<Void?>(null)

        val lock: ReentrantLock = locks.computeIfAbsent(eventId) { k: Long? -> ReentrantLock() }
        try {
            lock.lock()
            val e = eventService.findById(eventId)
            if (e == null) {
                log.warn("Add skipped: eventId {} not found", eventId)
                return CompletableFuture.completedFuture<Void?>(null)
            }
            if (!e.approved) {
                log.info("Add skipped: eventId {} not approved", eventId)
                return CompletableFuture.completedFuture<Void?>(null)
            }

            calendarService.add(e)
            eventService.update(e)
            log.info("Added eventId {} to Google Calendar as {}", eventId, e.getGoogleId())
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
        processing.remove(key("add", eventId))
        log.error("Giving up add for eventId {}: {}", eventId, ex.message, ex)
        return CompletableFuture.completedFuture<Void?>(null)
    }

    private fun key(op: String?, id: Long?): String {
        return op + "_" + id + "_" + (System.currentTimeMillis() / 10000)
    }

    companion object {
        private val log = LoggerFactory.getLogger(AddEventToCalendarJob::class.java)
        private val locks = ConcurrentHashMap<Long?, ReentrantLock>()
        private val processing = ConcurrentHashMap<String?, Boolean?>()
    }
}
