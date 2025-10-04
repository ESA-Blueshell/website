package net.blueshell.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.base.BaseController;
import net.blueshell.api.dto.event.EventDTO;
import net.blueshell.api.controller.filter.EventFilter;
import net.blueshell.api.mapper.event.EventMapper;
import net.blueshell.api.service.event.EventService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping
@Tag(name = "Events")
public class EventController extends BaseController<EventService, EventMapper> {

    @Autowired
    public EventController(EventService service, EventMapper mapper) {
        super(service, mapper);
    }

    @PreAuthorize("hasAuthority('BOARD') || hasPermission(#dto.committeeId, 'Committee', 'events')")
    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    public EventDTO createEvent(@Valid @RequestBody EventDTO dto) {
        var event = mapper.fromDTO(dto);
        event = service.create(event);
        return mapper.toDTO(event);
    }

//    @PreAuthorize("hasAuthority('BOARD') || hasPermission(#dto.committeeId, 'Committee', 'createEvent')")
//    @PostMapping("/events")
//    public EventDTO createOrUpdateEvent(@Valid @RequestBody EventDTO dto) {
//        Event event;
//        if (dto.getId() == null) {
//            event = mapper.fromDTO(dto);
//            event = service.create(event);
//        } else {
//            event = service.findById(dto.getId());
//            mapper.fromDTO(dto, event);
//            event = service.update(event);
//        }
//        return mapper.toDTO(event);
//    }

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

    @PreAuthorize("hasAuthority('BOARD')")
    @DeleteMapping("/events/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEventById(@PathVariable("eventId") Long eventId) {
        service.delete(eventId);
    }
}
