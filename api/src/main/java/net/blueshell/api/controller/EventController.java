package net.blueshell.api.controller;

import jakarta.validation.Valid;

import net.blueshell.api.base.BaseController;
import net.blueshell.api.dto.EventDTO;
import net.blueshell.api.mapper.EventMapper;
import net.blueshell.api.model.Event;
import net.blueshell.api.service.*;
import net.blueshell.api.auth.Identity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

@RestController
@RequestMapping("/events")
public class EventController extends BaseController<EventService, EventMapper> {

    private final EventSignUpService eventSignUpService;
    private final GuestService guestService;
    private final UserService userService;
    private final FileService fileService;

    @Autowired
    public EventController(EventService service, EventMapper mapper, EventSignUpService eventSignUpService, GuestService guestService, UserService userService, FileService fileService) {
        super(service, mapper);
        this.eventSignUpService = eventSignUpService;
        this.guestService = guestService;
        this.userService = userService;
        this.fileService = fileService;
    }

    @PreAuthorize("hasAuthority('COMMITTEE') && hasPermission(#eventDTO.committeeId, 'Committee', 'createEvent')")
    @PostMapping
    public EventDTO createEvent(@Valid @RequestBody EventDTO eventDTO) throws IOException {
        Event event = mapper.fromDTO(eventDTO);
        service.createEvent(event);
        return mapper.toDTO(event);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(#id, 'Event', 'read')")
    public EventDTO getEventById(
            @PathVariable("id") Long id) {
        Event event = service.findById(id);
        return mapper.toDTO(event);
    }


    @GetMapping
    public List<EventDTO> getEvents(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @RequestParam(required = false) OffsetDateTime from,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @RequestParam(required = false) OffsetDateTime to) {
        List<Event> events = service.findStartTimeBetween(from.toLocalDateTime(), to.toLocalDateTime());
        return mapper.toDTOs(events);
    }

    @GetMapping("/upcoming")
    public List<EventDTO> getUpcomingEvents(@RequestParam(required = false, defaultValue = "false") boolean editable) {

        List<Event> events = service.findUpcoming();
        return mapper.toDTOs(events);
    }

    @GetMapping("/past")
    public Stream<EventDTO> getPastEvents(@RequestParam(required = false, defaultValue = "false") boolean editable) {
        Identity authedUser = getPrincipal();
        List<Event> events = service.findAll();

        Predicate<Event> predicate = event -> {
            if (!event.isVisible() && !editable) {
                return false;
            }
            if (editable) {
                return true;
//                return event.canEdit(authedUser) && event.getStartTime().isBefore(LocalDateTime.now());
            }
            return event.isVisible() && event.getStartTime().isBefore(LocalDateTime.now());
        };

        Stream<Event> filteredEvents = events.stream()
                .filter(predicate)
                .sorted(Comparator.comparing(Event::getStartTime).reversed())
                .limit(30);

        return mapper.toDTOs(filteredEvents);
    }

    @GetMapping("/pageable")
    public Page<EventDTO> getEventsPageable(Pageable pageable) {
        Page<Event> events = service.findAll(pageable);
        return mapper.toDTOs(events);
    }

    @PreAuthorize("hasPermission(#eventId, 'Event', 'delete')")
    @DeleteMapping("/{eventId}")
    public void deleteEventById(@PathVariable("eventId") Long eventId) throws IOException {
        service.deleteEvent(eventId);
    }

    @PreAuthorize("hasPermission(#eventId, 'Event', 'read')")
    @PutMapping("/{eventId}")
    public EventDTO updateEvent(@PathVariable("eventId") Long eventId, @Valid @RequestBody EventDTO dto) throws IOException {
        Event event = mapper.fromDTO(dto);
        event.setId(eventId);
        Event updatedEvent = service.updateEvent(event);
        return mapper.toDTO(updatedEvent);
    }
}
