package net.blueshell.api.event.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.shared.web.BaseController
import net.blueshell.api.event.dto.EventSignUpDTO
import net.blueshell.api.event.mapper.EventSignUpMapper
import net.blueshell.api.event.model.filter.EventSignUpFilter
import net.blueshell.api.event.service.EventSignUpService
import org.springdoc.core.annotations.ParameterObject
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "EventSignUps")
class EventSignUpController @Autowired constructor(service: EventSignUpService, mapper: EventSignUpMapper) :
    BaseController<EventSignUpService, EventSignUpMapper>(service, mapper) {
    @GetMapping(value = ["/events/signups"])
    @PreAuthorize(
        "hasAuthority('BOARD') " +
                "or (#filter.userId != null && hasPermission(#filter.userId, 'User', 'read')) " +
                "or (#filter.committeeId != null && hasPermission(#filter.committeeId, 'Committee', 'events'))"
    )
    fun findEventSignUps(@ParameterObject filter: EventSignUpFilter = EventSignUpFilter()): MutableList<EventSignUpDTO> {
        val eventSignUps = service.findByFilter(filter)
        return mapper.toDTOs(eventSignUps.stream()).toList()
    }

    @GetMapping(value = ["/events/signups/byAccessToken/{accessToken}"])
    @PreAuthorize("#accessToken != null")
    fun findEventSignUpsByAccessToken(@PathVariable accessToken: String): MutableList<EventSignUpDTO> {
        val signUps = service.findByGuestAccessToken(accessToken)
        return mapper.toDTOs(signUps)
    }

    @GetMapping(value = ["/events/{eventId}/signups"])
    @PreAuthorize("hasAuthority('BOARD') || hasPermission(#eventId, 'Event', 'write')")
    fun findEventSignUpsByEventId(@PathVariable eventId: Long): MutableList<EventSignUpDTO> {
        val eventSignUps = service.findByEventId(eventId)
        return mapper.toDTOs(eventSignUps)
    }


    @PostMapping(value = ["/events/{eventId}/signups"])
    @PreAuthorize("hasAuthority('BOARD') or hasPermission(#dto.eventId, 'Event', 'signUp')")
    @ResponseStatus(HttpStatus.CREATED)
    fun createEventSignup(@Valid @RequestBody dto: EventSignUpDTO): EventSignUpDTO {
        principal?.id?.let { dto.userId = principal!!.id }
        var eventSignUp = mapper.fromDTO(dto)
        eventSignUp = service.create(eventSignUp)
        return mapper.toDTO(eventSignUp)
    }

    @PutMapping("/events/{eventId}/signups")
    @PreAuthorize(
        "hasAuthority('BOARD') " +
                "or hasPermission(#eventId, 'Event', 'signUp') " +
                "or (#accessToken != null and hasPermission(#accessToken, 'Guest', 'write'))"
    )
    fun updateEventSignUp(
        @PathVariable eventId: Long,
        @Valid @RequestBody dto: EventSignUpDTO,
        @RequestParam(value = "accessToken", required = false) accessToken: String?
    ): EventSignUpDTO {
        val signUp = if (accessToken == null) {
            val principalId = requireNotNull(principal?.id) { "User must be authenticated" }
            service.findByUserIdAndEventId(principalId, eventId)
        } else {
            service.findByGuestAccessTokenAndEventId(accessToken, eventId)
        }
        mapper.fromDTO(dto, signUp)
        val updated = service.update(signUp)
        return mapper.toDTO(updated)
    }


    @DeleteMapping(value = ["/events/signups/{eventSignupId}"])
    @PreAuthorize(
        "hasAuthority('BOARD') " +
                "or hasPermission(#eventSignupId, 'EventSignUp', 'delete') " +
                "or hasPermission(#accessToken, 'Guest', 'write')"
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteEventSignup(
        @PathVariable eventSignupId: Long,
        @RequestParam(value = "accessToken", required = false) accessToken: String?
    ) {
        service.deleteById(eventSignupId)
    }
}
