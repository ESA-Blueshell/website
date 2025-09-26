package net.blueshell.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import net.blueshell.api.base.BaseController;
import net.blueshell.api.dto.EventDTO;
import net.blueshell.api.controller.filter.EventFilter;
import net.blueshell.api.mapper.EventMapper;
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

    @PreAuthorize("hasAuthority('BOARD') || hasPermission(#eventDTO.committeeId, 'Committee', 'createEvent')")
    @PostMapping("/events")
    public EventDTO createEvent(@Valid @RequestBody EventDTO eventDTO) {
        var event = mapper.fromDTO(eventDTO);
        event = service.create(event);
        return mapper.toDTO(event);
    }

    @PreAuthorize("hasAuthority('BOARD') || (#id == dto.id && hasPermission(#id, 'Event', 'write'))")
    @PutMapping("/events/{id}")
    public EventDTO updateEvent(@PathVariable("id") Long id, @Valid @RequestBody EventDTO dto) {
        var event = service.findById(id);
        mapper.fromDTO(dto, event);
        event = service.update(event);
        return mapper.toDTO(event);
    }

    @GetMapping("/events/{id}")
    @PreAuthorize("hasPermission(#id, 'Event', 'read')")
    public EventDTO findEventById(@PathVariable("id") Long id) {
        var event = service.findById(id);
        return mapper.toDTO(event);
    }

    @GetMapping("/events")
    @PermitAll
    public Page<EventDTO> findEvents(@ParameterObject Pageable pageable, @ParameterObject EventFilter filter) {
        var events = service.findByFilter(pageable, filter);
        return mapper.toDTOs(events);
    }

    @PreAuthorize("hasPermission(#eventId, 'Event', 'delete')")
    @DeleteMapping("/events/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEventById(@PathVariable("eventId") Long eventId) {
        service.delete(eventId);
    }
}
