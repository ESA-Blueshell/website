package net.blueshell.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import net.blueshell.api.base.BaseController;
import net.blueshell.api.dto.EventDTO;
import net.blueshell.api.filter.EventFilter;
import net.blueshell.api.mapper.EventMapper;
import net.blueshell.api.model.Event;
import net.blueshell.api.service.EventService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping
@Tag(name = "Events")
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
    public EventDTO findEventById(
            @PathVariable("id") Long id) {
        Event event = service.findById(id);
        return mapper.toDTO(event);
    }

    @GetMapping("/events")
    public Page<EventDTO> findEvents(
            @ParameterObject Pageable pageable,
            @ParameterObject EventFilter filter
    ) {
        Page<Event> events = service.findByFilter(pageable, filter);
        return mapper.toDTOs(events);
    }

    @PreAuthorize("hasPermission(#eventId, 'Event', 'delete')")
    @DeleteMapping("/events/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
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
