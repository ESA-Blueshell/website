package net.blueshell.api.job.calendar;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.model.event.Event;
import net.blueshell.api.service.CalendarService;
import net.blueshell.api.service.event.EventService;
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
public class AddEventToCalendarJob {

    private static final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> processing = new ConcurrentHashMap<>();

    private final CalendarService calendarService;
    private final EventService eventService;

    @Async
    @Retryable(retryFor = {Exception.class, UncheckedIOException.class}, maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2))
    public CompletableFuture<Void> add(Long eventId) {
        String key = key("add", eventId);
        if (processing.putIfAbsent(key, true) != null) return CompletableFuture.completedFuture(null);

        ReentrantLock lock = locks.computeIfAbsent(eventId, k -> new ReentrantLock());
        try {
            lock.lock();
            Event e = eventService.findById(eventId);
            if (e == null) {
                log.warn("Add skipped: eventId {} not found", eventId);
                return CompletableFuture.completedFuture(null);
            }
            if (!e.isApproved()) {
                log.info("Add skipped: eventId {} not approved", eventId);
                return CompletableFuture.completedFuture(null);
            }

            calendarService.add(e);
            eventService.update(e);
            log.info("Added eventId {} to Google Calendar as {}", eventId, e.getGoogleId());
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
        processing.remove(key("add", eventId));
        log.error("Giving up add for eventId {}: {}", eventId, ex.getMessage(), ex);
        return CompletableFuture.completedFuture(null);
    }

    private String key(String op, Long id) {
        return op + "_" + id + "_" + (System.currentTimeMillis() / 10000);
    }
}
