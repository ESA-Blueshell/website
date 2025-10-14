package net.blueshell.api.job.google;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.model.event.Event;
import net.blueshell.api.service.CalendarService;
import net.blueshell.api.service.event.EventService;
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
@RequiredArgsConstructor
public class UpdateEventInCalendarJob {

    private static final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> processing = new ConcurrentHashMap<>();

    private final CalendarService calendarService;
    private final EventService eventService;

    @Async
    @Retryable(retryFor = {Exception.class, UncheckedIOException.class}, maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2))
    public CompletableFuture<Void> update(Long eventId) {
        String key = key("update", eventId);
        if (processing.putIfAbsent(key, true) != null) return CompletableFuture.completedFuture(null);

        ReentrantLock lock = locks.computeIfAbsent(eventId, k -> new ReentrantLock());
        try {
            lock.lock();
            Event e = eventService.findById(eventId);
            if (e == null) {
                log.warn("Update skipped: eventId {} not found", eventId);
                return CompletableFuture.completedFuture(null);
            }
            if (!e.isApproved()) {
                // reflect visibility change: remove if present
                if (e.getGoogleId() != null) {
                    calendarService.remove(e);
                    e.setGoogleId(null);
                    eventService.update(e);
                    log.info("EventId {} not approved anymore -> removed from Google", eventId);
                } else {
                    log.info("EventId {} not approved and not on Google -> noop", eventId);
                }
                return CompletableFuture.completedFuture(null);
            }

            if (e.getGoogleId() == null) {
                // Not on Google yet: treat as add
                calendarService.add(e);
                eventService.update(e);
                log.info("EventId {} was missing on Google; added as {}", eventId, e.getGoogleId());
            } else {
                calendarService.update(e);
                log.info("Updated eventId {} on Google (googleId={})", eventId, e.getGoogleId());
            }
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
        processing.remove(key("update", eventId));
        log.error("Giving up update for eventId {}: {}", eventId, ex.getMessage(), ex);
        return CompletableFuture.completedFuture(null);
    }

    private String key(String op, Long id) {
        return op + "_" + id + "_" + (System.currentTimeMillis() / 10000);
    }
}
