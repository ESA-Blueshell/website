package net.blueshell.api.job.calendar

import lombok.RequiredArgsConstructor
import lombok.extern.slf4j.Slf4j
import net.blueshell.api.service.CalendarService
import net.blueshell.api.service.event.EventService
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
@Slf4j
@RequiredArgsConstructor
class RemoveEventFromCalendarJob {
    private val calendarService: CalendarService? = null
    private val eventService: EventService? = null

    @Async
    @Retryable(
        retryFor = [Exception::class, UncheckedIOException::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 2000, multiplier = 2)
    )
    fun remove(eventId: Long?): CompletableFuture<Void?> {
        val key = key("remove", eventId)
        if (processing.putIfAbsent(key, true) != null) return CompletableFuture.completedFuture<Void?>(null)

        val lock: ReentrantLock = locks.computeIfAbsent(eventId) { k: Long? -> ReentrantLock() }
        try {
            lock.lock()
            val e = eventService!!.findById(eventId)
            if (e == null) {
                // If hard-deleted, consider keeping a shadow/audit of (eventId, googleId) to allow removal.
                RemoveEventFromCalendarJob.log.warn(
                    "Remove: eventId {} not found; if events are hard-deleted, persist googleId before deletion.",
                    eventId
                )
                return CompletableFuture.completedFuture<Void?>(null)
            }
            if (e.getGoogleId() == null) {
                RemoveEventFromCalendarJob.log.info("Remove skipped: eventId {} not present on Google", eventId)
                return CompletableFuture.completedFuture<Void?>(null)
            }

            calendarService!!.remove(e)
            e.setGoogleId(null)
            eventService.update(e) // persist null googleId
            RemoveEventFromCalendarJob.log.info("Removed eventId {} from Google Calendar", eventId)
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
        processing.remove(key("remove", eventId))
        RemoveEventFromCalendarJob.log.error("Giving up remove for eventId {}: {}", eventId, ex.message, ex)
        return CompletableFuture.completedFuture<Void?>(null)
    }

    private fun key(op: String?, id: Long?): String {
        return op + "_" + id + "_" + (System.currentTimeMillis() / 10000)
    }

    companion object {
        private val locks = ConcurrentHashMap<Long?, ReentrantLock>()
        private val processing = ConcurrentHashMap<String?, Boolean?>()
    }
}
