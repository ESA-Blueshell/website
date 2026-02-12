package net.blueshell.api.domain.event.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.domain.event.command.*
import net.blueshell.api.domain.event.persistence.filter.EventSignUpFilter
import net.blueshell.api.domain.event.web.dto.CreateEventSignUpRequest
import net.blueshell.api.domain.event.web.dto.EventSignUpResponse
import net.blueshell.api.domain.event.web.dto.UpdateEventSignUpRequest
import net.blueshell.api.domain.event.web.mapping.asCommand
import net.blueshell.api.domain.event.web.mapping.asResponse
import net.blueshell.api.infrastructure.security.UserPrincipal
import net.blueshell.api.shared.command.CommandBus
import net.blueshell.api.shared.web.BaseController
import org.springdoc.core.annotations.ParameterObject
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "EventSignUps")
class EventSignUpController @Autowired constructor(
    service: net.blueshell.api.domain.event.application.EventSignUpService,
    private val commandBus: CommandBus
) : BaseController<net.blueshell.api.domain.event.application.EventSignUpService>(service) {
    @GetMapping(value = ["/events/signups"])
    @PreAuthorize(
        "hasAuthority('BOARD') " +
                "or (#filter.userId != null && hasPermission(#filter.userId, 'User', 'read')) " +
                "or (#filter.committeeId != null && hasPermission(#filter.committeeId, 'Committee', 'events'))"
    )
    fun findEventSignUps(@ParameterObject filter: EventSignUpFilter = EventSignUpFilter()): MutableList<EventSignUpResponse> {
        val eventSignUps = commandBus.dispatch(FindEventSignUpsCommand(filter))
        return eventSignUps.map { it.asResponse() }.toMutableList()
    }

    @GetMapping(value = ["/events/signups/byAccessToken/{accessToken}"])
    @PreAuthorize("#accessToken != null")
    fun findEventSignUpsByAccessToken(@PathVariable accessToken: String): MutableList<EventSignUpResponse> {
        val signUps = commandBus.dispatch(FindEventSignUpsByAccessTokenCommand(accessToken))
        return signUps.map { it.asResponse() }.toMutableList()
    }

    @GetMapping(value = ["/events/{eventId}/signups"])
    @PreAuthorize("hasAuthority('BOARD') || hasPermission(#eventId, 'Event', 'write')")
    fun findEventSignUpsByEventId(@PathVariable eventId: Long): MutableList<EventSignUpResponse> {
        val eventSignUps = commandBus.dispatch(FindEventSignUpsByEventIdCommand(eventId))
        return eventSignUps.map { it.asResponse() }.toMutableList()
    }


    @PostMapping(value = ["/events/{eventId}/signups"])
    @PreAuthorize("hasAuthority('BOARD') or hasPermission(#eventId, 'Event', 'signUp')")
    @ResponseStatus(HttpStatus.CREATED)
    fun createEventSignup(
        @PathVariable eventId: Long,
        @Valid @RequestBody request: CreateEventSignUpRequest,
        @AuthenticationPrincipal principal: UserPrincipal?
    ): EventSignUpResponse {
        val eventSignUp = commandBus.dispatch(request.asCommand(eventId, principal?.id))
        return eventSignUp.asResponse()
    }

    @PutMapping("/events/{eventId}/signups")
    @PreAuthorize(
        "hasAuthority('BOARD') " +
                "or hasPermission(#eventId, 'Event', 'signUp') " +
                "or (#accessToken != null and hasPermission(#accessToken, 'Guest', 'write'))"
    )
    fun updateEventSignUp(
        @PathVariable eventId: Long,
        @Valid @RequestBody request: UpdateEventSignUpRequest,
        @RequestParam(value = "accessToken", required = false) accessToken: String?,
        @AuthenticationPrincipal principal: UserPrincipal?
    ): EventSignUpResponse {
        val updated = commandBus.dispatch(
            request.asCommand(eventId, principal?.id, accessToken)
        )
        return updated.asResponse()
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
        commandBus.dispatch(DeleteEventSignUpCommand(eventSignupId))
    }
}
