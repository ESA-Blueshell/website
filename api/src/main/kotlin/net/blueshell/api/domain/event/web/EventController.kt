package net.blueshell.api.domain.event.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import jakarta.ws.rs.QueryParam
import net.blueshell.api.domain.event.command.*
import net.blueshell.api.domain.event.persistence.filter.EventFilter
import net.blueshell.api.domain.event.web.dto.CreateEventRequest
import net.blueshell.api.domain.event.web.dto.EventResponse
import net.blueshell.api.domain.event.web.dto.UpdateEventRequest
import net.blueshell.api.domain.event.web.mapping.asResponse
import net.blueshell.api.shared.command.CommandBus
import net.blueshell.api.shared.web.BaseController
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping
@Tag(name = "Events")
class EventController(
    service: net.blueshell.api.domain.event.application.EventService,
    private val commandBus: CommandBus
) : BaseController<net.blueshell.api.domain.event.application.EventService>(service) {
    @PreAuthorize("hasAuthority('BOARD') || hasPermission(#request.committeeId, 'Committee', 'events')")
    @PostMapping("/events")
    @ResponseStatus(
        HttpStatus.CREATED
    )
    fun createEvent(@Valid @RequestBody request: CreateEventRequest): EventResponse {
        val event = commandBus.dispatch(CreateEventCommand(request))
        return event.asResponse()
    }

    @PreAuthorize("hasAuthority('BOARD') || hasPermission(#id, 'Event', 'write')")
    @PutMapping("/events/{id}")
    fun updateEvent(@PathVariable id: Long, @Valid @RequestBody request: UpdateEventRequest): EventResponse {
        val event = commandBus.dispatch(UpdateEventCommand(id, request))
        return event.asResponse()
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PutMapping("/events/{id}/approve")
    fun approveEvent(@PathVariable id: Long, @QueryParam(value = "approved") approved: Boolean): EventResponse {
        val event = commandBus.dispatch(ApproveEventCommand(id, approved))
        return event.asResponse()
    }

    @GetMapping("/events/{id}")
    @PreAuthorize("hasAuthority('BOARD') || hasPermission(#id, 'Event', 'read')")
    fun findEventById(@PathVariable id: Long): EventResponse {
        val event = commandBus.dispatch(FindEventByIdCommand(id))
        return event.asResponse()
    }

    @GetMapping("/events")
    @PermitAll
    fun findEvents(
        @ParameterObject pageable: Pageable = Pageable.unpaged(),
        @ParameterObject filter: EventFilter = EventFilter()
    ): Page<EventResponse> {
        val events = commandBus.dispatch(FindEventsCommand(pageable, filter))
        return events.map { it.asResponse() }
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @DeleteMapping("/events/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteEventById(@PathVariable eventId: Long) {
        commandBus.dispatch(DeleteEventByIdCommand(eventId))
    }
}
