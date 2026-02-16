package net.blueshell.api.domain.event.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.domain.event.application.EventSignUpService
import net.blueshell.api.domain.event.command.*
import net.blueshell.api.domain.event.application.query.EventSignUpQuery
import net.blueshell.api.domain.event.web.dto.request.CreateEventSignUpRequest
import net.blueshell.api.domain.event.web.dto.response.EventSignUpResponse
import net.blueshell.api.domain.event.web.dto.request.UpdateEventSignUpRequest
import net.blueshell.api.domain.event.web.mapping.asCommand
import net.blueshell.api.domain.event.web.mapping.asResponse
import net.blueshell.api.shared.security.UserPrincipal
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
    service: EventSignUpService,
    private val commandBus: CommandBus
) : BaseController<EventSignUpService>(service) {
    @GetMapping(value = ["/events/signups"])
    @PreAuthorize(
        "hasPermission(null, 'Event', 'signups') " +
                "or (#filter.userId != null && hasPermission(#filter.userId, 'User', 'read')) " +
                "or (#filter.committeeId != null && hasPermission(#filter.committeeId, 'Committee', 'events'))"
    )
    fun findEventSignUps(@ParameterObject filter: EventSignUpQuery = EventSignUpQuery()): List<EventSignUpResponse> {
        val eventSignUps = commandBus.dispatch(FindEventSignUpsCommand(filter))
        return eventSignUps.map { it.asResponse() }
    }

    @GetMapping(value = ["/events/signups/byAccessToken/{accessToken}"])
    @PreAuthorize("hasPermission(#accessToken, 'Guest', 'read')")
    fun findEventSignUpsByAccessToken(@PathVariable(required = true) accessToken: String): List<EventSignUpResponse> {
        val signUps = commandBus.dispatch(FindEventSignUpsByAccessTokenCommand(accessToken))
        return signUps.map { it.asResponse() }
    }

    @GetMapping(value = ["/events/{eventId}/signups"])
    @PreAuthorize("hasPermission(#eventId, 'Event', 'write')")
    fun findEventSignUpsByEventId(@PathVariable eventId: Long): List<EventSignUpResponse> {
        val eventSignUps = commandBus.dispatch(FindEventSignUpsByEventIdCommand(eventId))
        return eventSignUps.map { it.asResponse() }
    }


    @PostMapping(value = ["/events/{eventId}/signups"])
    @PreAuthorize("hasPermission(#eventId, 'Event', 'signUp')")
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
        "hasPermission(#eventId, 'Event', 'signUp') " +
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


    @DeleteMapping(value = ["/events/signups/{id}"])
    @PreAuthorize(
        "hasPermission(#id, 'EventSignUp', 'delete') " +
                "or hasPermission(#accessToken, 'Guest', 'write')"
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteEventSignup(
        @PathVariable id: Long,
        @RequestParam(value = "accessToken", required = false) accessToken: String?
    ) {
        commandBus.dispatch(DeleteEventSignUpCommand(id))
    }
}
