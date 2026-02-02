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
class AddEventToCalendarJob {
    private val calendarService: CalendarService? = null
    private val eventService: EventService? = null

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
            val e = eventService!!.findById(eventId)
            if (e == null) {
                AddEventToCalendarJob.log.warn("Add skipped: eventId {} not found", eventId)
                return CompletableFuture.completedFuture<Void?>(null)
            }
            if (!e.isApproved()) {
                AddEventToCalendarJob.log.info("Add skipped: eventId {} not approved", eventId)
                return CompletableFuture.completedFuture<Void?>(null)
            }

            calendarService!!.add(e)
            eventService.update(e)
            AddEventToCalendarJob.log.info("Added eventId {} to Google Calendar as {}", eventId, e.getGoogleId())
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
        AddEventToCalendarJob.log.error("Giving up add for eventId {}: {}", eventId, ex.message, ex)
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
