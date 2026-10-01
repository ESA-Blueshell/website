package net.blueshell.api.event.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import net.blueshell.api.event.api.AnnounceChoice
import net.blueshell.api.event.api.AnnouncementLedger
import net.blueshell.api.event.api.EventService
import net.blueshell.api.event.domain.EventQuery
import net.blueshell.api.event.domain.EventUseCases
import net.blueshell.api.event.persistence.Event
import net.blueshell.api.shared.web.BaseController
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping
@Tag(name = "Events")
class EventController(
    service: EventService,
    private val useCases: EventUseCases,
    private val announcements: AnnouncementLedger,
) : BaseController<EventService>(service) {
    // Only one event's answer says whether its post is out: a list would ask once per row.
    private fun Event.asOneResponse(): EventResponse = asResponse().also { it.announced = announcements.announced(id!!) }

    @PreAuthorize("hasPermission(#request.committeeId, 'Committee', 'events')")
    @PostMapping("/events")
    @ResponseStatus(
        HttpStatus.CREATED,
    )
    fun createEvent(
        @Valid @RequestBody request: CreateEventRequest,
    ): EventResponse {
        val event = useCases.create(request.asData(), request.announce)
        return event.asOneResponse()
    }

    @PreAuthorize("hasPermission(#id, 'Event', 'write') and hasPermission(#request.committeeId, 'Committee', 'events')")
    @PutMapping("/events/{id}")
    fun updateEvent(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateEventRequest,
    ): EventResponse {
        val event =
            useCases.update(
                id = id,
                data = request.asData(),
                removeExistingSignUps = request.removeExistingSignUps == true,
                version = request.version,
                announce = request.announce,
            )
        return event.asOneResponse()
    }

    @PreAuthorize("hasPermission(#id, 'Event', 'approve')")
    @PutMapping("/events/{id}/approve")
    fun approveEvent(
        @PathVariable id: Long,
        @RequestParam approved: Boolean,
        @RequestParam(required = false) announce: AnnounceChoice?,
    ): EventResponse {
        val event = useCases.approve(id, approved, announce)
        return event.asOneResponse()
    }

    @GetMapping("/events/{id}")
    @PreAuthorize("hasPermission(#id, 'Event', 'read')")
    fun findEventById(
        @PathVariable id: Long,
    ): EventResponse {
        val event = service.findById(id)
        return event.asOneResponse()
    }

    @GetMapping("/events")
    @PermitAll
    fun findEvents(
        @ParameterObject pageable: Pageable = Pageable.unpaged(),
        @ParameterObject filter: EventQuery = EventQuery(),
    ): Page<EventResponse> {
        val events = service.findByFilter(pageable, filter)
        return events.map { it.asResponse() }
    }

    @PreAuthorize("hasPermission(#eventId, 'Event', 'delete')")
    @DeleteMapping("/events/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteEventById(
        @PathVariable eventId: Long,
    ) {
        service.deleteById(eventId)
    }
}
