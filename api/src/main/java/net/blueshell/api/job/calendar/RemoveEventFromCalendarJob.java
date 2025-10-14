package net.blueshell.api.job.calendar;

import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.model.event.Event;
import net.blueshell.api.service.event.EventService;
import net.blueshell.api.service.CalendarService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Slf4j
public class RemoveEventFromCalendarJob {

    private static final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> processing = new ConcurrentHashMap<>();

    @Autowired
    private CalendarService calendarService;
    @Autowired
    private EventService eventService;

    @Async
    @Retryable(retryFor = {Exception.class, UncheckedIOException.class}, maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2))
    public CompletableFuture<Void> remove(Long eventId) {
        String key = key("remove", eventId);
        if (processing.putIfAbsent(key, true) != null) return CompletableFuture.completedFuture(null);

        ReentrantLock lock = locks.computeIfAbsent(eventId, k -> new ReentrantLock());
        try {
            lock.lock();
            Event e = eventService.findById(eventId);
            if (e == null) {
                // If hard-deleted, consider keeping a shadow/audit of (eventId, googleId) to allow removal.
                log.warn("Remove: eventId {} not found; if events are hard-deleted, persist googleId before deletion.", eventId);
                return CompletableFuture.completedFuture(null);
            }
            if (e.getGoogleId() == null) {
                log.info("Remove skipped: eventId {} not present on Google", eventId);
                return CompletableFuture.completedFuture(null);
            }

            calendarService.remove(e);
            e.setGoogleId(null);
            eventService.update(e); // persist null googleId
            log.info("Removed eventId {} from Google Calendar", eventId);
            return CompletableFuture.completedFuture(null);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } finally {
            lock.unlock();
            processing.remove(key);
        }
    }

    @Recover
    public CompletableFuture<Void> recover(Exception ex, Long eventId) {
        processing.remove(key("remove", eventId));
        log.error("Giving up remove for eventId {}: {}", eventId, ex.getMessage(), ex);
        return CompletableFuture.completedFuture(null);
    }

    private String key(String op, Long id) {
        return op + "_" + id + "_" + (System.currentTimeMillis() / 10000);
    }
}
