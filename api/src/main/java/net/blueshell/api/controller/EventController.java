package net.blueshell.api.controller;

import jakarta.validation.Valid;
import net.blueshell.api.base.BaseController;
import net.blueshell.api.dto.EventDTO;
import net.blueshell.api.mapper.EventMapper;
import net.blueshell.api.model.Event;
import net.blueshell.api.model.User;
import net.blueshell.api.service.EventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

@RestController
@RequestMapping
public class EventController extends BaseController<EventService, EventMapper> {

    @Autowired
    public EventController(EventService service, EventMapper mapper) {
        super(service, mapper);
    }

    @PreAuthorize("hasAuthority('COMMITTEE') && hasPermission(#eventDTO.committeeId, 'Committee', 'createEvent')")
    @PostMapping("/events")
    public EventDTO createEvent(@Valid @RequestBody EventDTO eventDTO) {
        Event event = mapper.fromDTO(eventDTO);
        service.create(event);
        return mapper.toDTO(event);
    }

    @GetMapping("/events/{id}")
    @PreAuthorize("hasPermission(#id, 'Event', 'read')")
    public EventDTO getEventById(
            @PathVariable("id") Long id) {
        Event event = service.findById(id);
        return mapper.toDTO(event);
    }


    @GetMapping("/events")
    public List<EventDTO> getEvents(
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime to) {
        List<Event> events = service.findStartTimeBetween(
                from != null ? from.toLocalDateTime() : null,
                to != null ? to.toLocalDateTime() : null);
        return mapper.toDTOs(events);
    }

    @GetMapping("/upcoming")
    public List<EventDTO> getUpcomingEvents(@RequestParam(required = false, defaultValue = "false") boolean editable) {

        List<Event> events = service.findUpcoming();
        return mapper.toDTOs(events);
    }

    @GetMapping("/events/past")
    public Stream<EventDTO> getPastEvents(@RequestParam(required = false, defaultValue = "false") boolean editable) {
        User authedUser = getPrincipal();
        List<Event> events = service.findAll();

        Predicate<Event> predicate = event -> {
            if (!event.isVisible() && !editable) {
                return false;
            }
            if (editable) {
                return true;
//                return event.canEdit(authedUser) && event.getStartTime().isBefore(LocalDateTime.now());
            }
            return event.getStartTime().isBefore(LocalDateTime.now());
        };

        Stream<Event> filteredEvents = events.stream()
                .filter(predicate)
                .sorted(Comparator.comparing(Event::getStartTime).reversed())
                .limit(30);

        return mapper.toDTOs(filteredEvents);
    }

    @GetMapping("/events/pageable")
    public Page<EventDTO> getEventsPageable(Pageable pageable) {
        Page<Event> events = service.findAll(pageable);
        return mapper.toDTOs(events);
    }

    @PreAuthorize("hasPermission(#eventId, 'Event', 'delete')")
    @DeleteMapping("/events/{eventId}")
    public void deleteEventById(@PathVariable("eventId") Long eventId) {
        service.delete(eventId);
    }

    @PreAuthorize("hasPermission(#eventId, 'Event', 'read')")
    @PutMapping("/events/{eventId}")
    public EventDTO updateEvent(@PathVariable("eventId") Long eventId, @Valid @RequestBody EventDTO dto) {
        Event event = mapper.fromDTO(dto);
        event.setId(eventId);
        service.update(event);
        return mapper.toDTO(event);
    }
}
