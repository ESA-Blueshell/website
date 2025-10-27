package net.blueshell.api.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.model.event.Event;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@Primary
public class MockCalendarService extends CalendarService {

    private final AtomicLong seq = new AtomicLong(1_000_000L);

    private final Map<String, Event> eventsById = new ConcurrentHashMap<>();

    @Getter
    private final Map<String, Event> readOnlyEvents =
            Collections.unmodifiableMap(eventsById);

    @Override
    public void add(@NotNull Event event) throws IOException {
        if (event.getGoogleId() != null) {
            update(event);
            return;
        }
        String id = "mock-" + seq.getAndIncrement();
        Event stored = copyOf(event);
        stored.setGoogleId(id);
        eventsById.put(id, stored);
        event.setGoogleId(id);
        log.info("[calendar-mock] added event id={} title='{}' start={} end={}",
                id, event.getTitle(), event.getStartTime(), event.getEndTime());
    }

    @Override
    public void update(@NotNull Event event) throws IOException {
        if (event.getGoogleId() == null) {
            add(event);
            return;
        }
        String id = event.getGoogleId();
        eventsById.put(id, copyOf(event));
        log.info("[calendar-mock] updated event id={} title='{}'", id, event.getTitle());
    }

    @Override
    public void remove(@NotNull Event event) throws IOException {
        String id = event.getGoogleId();
        if (id == null) return;
        eventsById.remove(id);
        event.setGoogleId(null);
        log.info("[calendar-mock] removed event id={}", id);
    }

    @Override
    public void sync(@NotNull Event event) throws IOException {
        if (event.getGoogleId() == null) add(event);
        else update(event);
    }

    public void clear() {
        eventsById.clear();
    }

    public Event findByGoogleId(String googleId) {
        Event e = eventsById.get(googleId);
        return e == null ? null : copyOf(e);
    }

    public Map<String, Event> findBetween(Instant startInclusive, Instant endExclusive) {
        return eventsById.entrySet().stream()
                .filter(en -> {
                    Instant s = en.getValue().getStartTime();
                    Instant e = en.getValue().getEndTime();
                    return s != null && e != null &&
                            !s.isBefore(startInclusive) && e.isBefore(endExclusive);
                })
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static Event copyOf(Event src) {
        Event e = new Event();
        e.setId(src.getId());
        e.setTitle(src.getTitle());
        e.setLocation(src.getLocation());
        e.setDescription(src.getDescription());
        e.setStartTime(src.getStartTime());
        e.setEndTime(src.getEndTime());
        e.setGoogleId(src.getGoogleId());
        return e;
    }
}
