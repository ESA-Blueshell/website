package net.blueshell.api.event.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import jakarta.ws.rs.QueryParam
import net.blueshell.api.shared.web.BaseController
import net.blueshell.api.event.web.dto.EventDTO
import net.blueshell.api.event.web.mapping.asDto
import net.blueshell.api.event.persistence.filter.EventFilter
import net.blueshell.api.event.application.EventService
import net.blueshell.api.event.web.mapping.asEntity
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
class EventController @Autowired constructor(service: EventService) :
    BaseController<EventService>(service) {
    @PreAuthorize("hasAuthority('BOARD') || hasPermission(#dto.committeeId, 'Committee', 'events')")
    @PostMapping("/events")
    @ResponseStatus(
        HttpStatus.CREATED
    )
    fun createEvent(@Valid @RequestBody dto: EventDTO): EventDTO {
        var event = dto.asEntity()
        event = service.create(event)
        return event.asDto()
    }

    @PreAuthorize("hasAuthority('BOARD') || (#id == #dto.id && hasPermission(#id, 'Event', 'write'))")
    @PutMapping("/events/{id}")
    fun updateEvent(@PathVariable id: Long, @Valid @RequestBody dto: EventDTO): EventDTO {
        var event = service.findById(id)
        dto.asEntity(event)
        event = service.update(event)
        return event.asDto()
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PutMapping("/events/{id}/approve")
    fun approveEvent(@PathVariable id: Long, @QueryParam(value = "approved") approved: Boolean): EventDTO {
        var event = service.findById(id)
        event.approved = approved
        event = service.update(event)
        return event.asDto()
    }

    @GetMapping("/events/{id}")
    @PreAuthorize("hasAuthority('BOARD') || hasPermission(#id, 'Event', 'read')")
    fun findEventById(@PathVariable id: Long): EventDTO {
        val event = service.findById(id)
        return event.asDto()
    }

    @GetMapping("/events")
    @PermitAll
    fun findEvents(
        @ParameterObject pageable: Pageable = Pageable.unpaged(),
        @ParameterObject filter: EventFilter = EventFilter()
    ): Page<EventDTO> {
        val events = service.findByFilter(pageable, filter)
        return events.map { it.asDto() }
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @DeleteMapping("/events/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteEventById(@PathVariable eventId: Long) {
        service.deleteById(eventId)
    }
}
