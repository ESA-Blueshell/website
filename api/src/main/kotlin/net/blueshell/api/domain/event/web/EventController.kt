package net.blueshell.api.domain.event.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import jakarta.ws.rs.QueryParam
import net.blueshell.api.domain.event.application.EventService
import net.blueshell.api.domain.event.application.query.EventQuery
import net.blueshell.api.domain.event.command.ApproveEventCommand
import net.blueshell.api.domain.event.command.DeleteEventByIdCommand
import net.blueshell.api.domain.event.command.FindEventByIdCommand
import net.blueshell.api.domain.event.command.FindEventsCommand
import net.blueshell.api.domain.event.web.dto.request.CreateEventRequest
import net.blueshell.api.domain.event.web.dto.request.UpdateEventRequest
import net.blueshell.api.domain.event.web.dto.response.EventResponse
import net.blueshell.api.domain.event.web.mapping.request.asCommand
import net.blueshell.api.domain.event.web.mapping.response.asResponse
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
    service: EventService,
    private val commandBus: CommandBus
) : BaseController<EventService>(service) {
    @PreAuthorize("hasPermission(#request.committeeId, 'Committee', 'events')")
    @PostMapping("/events")
    @ResponseStatus(
        HttpStatus.CREATED
    )
    fun createEvent(@Valid @RequestBody request: CreateEventRequest): EventResponse {
        val event = commandBus.dispatch(request.asCommand())
        return event.asResponse()
    }

    @PreAuthorize("hasPermission(#id, 'Event', 'write')")
    @PutMapping("/events/{id}")
    fun updateEvent(@PathVariable id: Long, @Valid @RequestBody request: UpdateEventRequest): EventResponse {
        val event = commandBus.dispatch(request.asCommand(id))
        return event.asResponse()
    }

    @PreAuthorize("hasPermission(#id, 'Event', 'approve')")
    @PutMapping("/events/{id}/approve")
    fun approveEvent(@PathVariable id: Long, @QueryParam(value = "approved") approved: Boolean): EventResponse {
        val event = commandBus.dispatch(ApproveEventCommand(id, approved))
        return event.asResponse()
    }

    @GetMapping("/events/{id}")
    @PreAuthorize("hasPermission(#id, 'Event', 'read')")
    fun findEventById(@PathVariable id: Long): EventResponse {
        val event = commandBus.dispatch(FindEventByIdCommand(id))
        return event.asResponse()
    }

    @GetMapping("/events")
    @PermitAll
    fun findEvents(
        @ParameterObject pageable: Pageable = Pageable.unpaged(),
        @ParameterObject filter: EventQuery = EventQuery()
    ): Page<EventResponse> {
        val events = commandBus.dispatch(FindEventsCommand(pageable, filter))
        return events.map { it.asResponse() }
    }

    @PreAuthorize("hasPermission(#eventId, 'Event', 'delete')")
    @DeleteMapping("/events/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteEventById(@PathVariable eventId: Long) {
        commandBus.dispatch(DeleteEventByIdCommand(eventId))
    }
}
