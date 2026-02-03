package net.blueshell.api.controller.event

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import jakarta.ws.rs.QueryParam
import net.blueshell.api.base.BaseController
import net.blueshell.api.controller.filter.EventFilter
import net.blueshell.api.dto.event.EventDTO
import net.blueshell.api.mapper.event.EventMapper
import net.blueshell.api.service.event.EventService
import org.springdoc.core.annotations.ParameterObject
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping
@Tag(name = "Events")
class EventController @Autowired constructor(service: EventService, mapper: EventMapper) :
    BaseController<EventService, EventMapper>(service, mapper) {
    @PreAuthorize("hasAuthority('BOARD') || hasPermission(#dto.committeeId, 'Committee', 'events')")
    @PostMapping("/events")
    @ResponseStatus(
        HttpStatus.CREATED
    )
    fun createEvent(@Valid @RequestBody dto: @Valid EventDTO?): EventDTO? {
        var event = mapper.fromDTO(dto)
        event = service.create(event)
        return mapper.toDTO(event)
    }

    @PreAuthorize("hasAuthority('BOARD') || (#id == #dto.id && hasPermission(#id, 'Event', 'write'))")
    @PutMapping("/events/{id}")
    fun updateEvent(@PathVariable("id") id: Long?, @Valid @RequestBody dto: @Valid EventDTO?): EventDTO? {
        var event = service.findById(id)
        mapper.fromDTO(dto, event)
        event = service.update(event)
        return mapper.toDTO(event)
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PutMapping("/events/{id}/approve")
    fun approveEvent(@PathVariable("id") id: Long?, @QueryParam(value = "approved") approved: Boolean): EventDTO? {
        var event = service.findById(id)
        event.setApproved(approved)
        event = service.update(event)
        return mapper.toDTO(event)
    }

    @GetMapping("/events/{id}")
    @PreAuthorize("hasAuthority('BOARD') || hasPermission(#id, 'Event', 'read')")
    fun findEventById(@PathVariable("id") id: Long?): EventDTO? {
        val event = service.findById(id)
        return mapper.toDTO(event)
    }

    @GetMapping("/events")
    @PermitAll
    fun findEvents(@ParameterObject pageable: Pageable?, @ParameterObject filter: EventFilter?): Page<EventDTO?>? {
        val events = service.findByFilter(pageable, filter)
        return mapper.toDTOs(events)
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @DeleteMapping("/events/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteEventById(@PathVariable("eventId") eventId: Long?) {
        service.deleteById(eventId)
    }
}
