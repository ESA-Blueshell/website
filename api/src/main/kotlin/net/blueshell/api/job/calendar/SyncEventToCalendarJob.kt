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
class SyncEventToCalendarJob {
    private val calendarService: CalendarService? = null
    private val eventService: EventService? = null

    @Async
    @Retryable(
        retryFor = [Exception::class, UncheckedIOException::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 2000, multiplier = 2)
    )
    fun sync(eventId: Long?): CompletableFuture<Void?> {
        val key = key("sync", eventId)
        if (processing.putIfAbsent(key, true) != null) return CompletableFuture.completedFuture<Void?>(null)

        val lock: ReentrantLock = locks.computeIfAbsent(eventId) { k: Long? -> ReentrantLock() }
        try {
            lock.lock()
            val e = eventService!!.findById(eventId)
            if (e == null) {
                SyncEventToCalendarJob.log.warn("Sync skipped: eventId {} not found", eventId)
                return CompletableFuture.completedFuture<Void?>(null)
            }

            if (!e.isApproved()) {
                // remove if previously on Google
                if (e.getGoogleId() != null) {
                    calendarService!!.remove(e)
                    e.setGoogleId(null)
                    eventService.update(e)
                    SyncEventToCalendarJob.log.info("EventId {} unapproved -> removed from Google", eventId)
                } else {
                    SyncEventToCalendarJob.log.info("EventId {} unapproved and not on Google -> noop", eventId)
                }
                return CompletableFuture.completedFuture<Void?>(null)
            }

            // approved: add or update
            if (e.getGoogleId() == null) {
                calendarService!!.add(e)
                eventService.update(e)
                SyncEventToCalendarJob.log.info("EventId {} synced by add; googleId={}", eventId, e.getGoogleId())
            } else {
                calendarService!!.update(e)
                SyncEventToCalendarJob.log.info("EventId {} synced by update; googleId={}", eventId, e.getGoogleId())
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
        SyncEventToCalendarJob.log.error("Giving up sync for eventId {}: {}", eventId, ex.message, ex)
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
