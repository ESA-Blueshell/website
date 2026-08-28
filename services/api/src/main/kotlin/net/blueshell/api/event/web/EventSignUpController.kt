package net.blueshell.api.event.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import net.blueshell.api.event.domain.EventSignUpService
import net.blueshell.api.event.domain.EventSignUpUseCases
import net.blueshell.api.event.domain.EventSignUpQuery
import net.blueshell.api.shared.security.UserPrincipal
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
    private val useCases: EventSignUpUseCases,
) : BaseController<EventSignUpService>(service) {
    companion object {
        const val GUEST_ACCESS_TOKEN_HEADER = "X-Guest-Access-Token"
    }

    @GetMapping(value = ["/events/signups"])
    @PreAuthorize(
        "hasPermission('__NO_TARGET__', 'Event', 'signups') " +
                "or (#filter.userId != null && hasPermission(#filter.userId, 'User', 'read')) " +
                "or (#filter.committeeId != null && hasPermission(#filter.committeeId, 'Committee', 'events'))"
    )
    fun findEventSignUps(@ParameterObject filter: EventSignUpQuery = EventSignUpQuery()): List<EventSignUpResponse> {
        val eventSignUps = service.findByFilter(filter)
        return eventSignUps.map { it.asResponse() }
    }

    @GetMapping(value = ["/events/signups/byAccessToken"])
    @PreAuthorize("hasPermission(#accessToken, 'Guest', 'read')")
    fun findEventSignUpsByAccessToken(
        @RequestHeader(name = GUEST_ACCESS_TOKEN_HEADER) accessToken: String
    ): List<EventSignUpResponse> {
        val signUps = service.findByGuestAccessToken(accessToken)
        return signUps.map { it.asResponse() }
    }

    @GetMapping(value = ["/events/{eventId}/signups"])
    @PreAuthorize("hasPermission(#eventId, 'Event', 'write')")
    fun findEventSignUpsByEventId(@PathVariable eventId: Long): List<EventSignUpResponse> {
        val eventSignUps = service.findByEventId(eventId)
        return eventSignUps.map { it.asResponse() }
    }


    @PostMapping(value = ["/events/{eventId}/signups"])
    @PreAuthorize("hasPermission(#eventId, 'Event', 'signUp')")
    @ResponseStatus(HttpStatus.CREATED)
    fun createEventSignup(
        @PathVariable eventId: Long,
        @Valid @RequestBody request: CreateEventSignUpRequest,
        @AuthenticationPrincipal principal: UserPrincipal?,
        response: HttpServletResponse
    ): EventSignUpResponse {
        val eventSignUp = useCases.create(request.asData(eventId), principal?.id)
        eventSignUp.guest?.accessTokenRaw?.let { response.setHeader(GUEST_ACCESS_TOKEN_HEADER, it) }
        return eventSignUp.asResponse()
    }

    @PutMapping("/events/{eventId}/signups")
    @PreAuthorize(
        "hasPermission(#eventId, 'Event', 'signUp') " +
                "or (#guestAccessToken != null and hasPermission(#guestAccessToken, 'Guest', 'write'))"
    )
    fun updateEventSignUp(
        @PathVariable eventId: Long,
        @Valid @RequestBody request: UpdateEventSignUpRequest,
        @RequestHeader(name = GUEST_ACCESS_TOKEN_HEADER, required = false) guestAccessToken: String?,
        @AuthenticationPrincipal principal: UserPrincipal?,
        response: HttpServletResponse
    ): EventSignUpResponse {
        val updated = useCases.update(
            eventId = eventId,
            data = request.asData(eventId),
            principalId = principal?.id,
            accessToken = guestAccessToken,
        )
        if (!guestAccessToken.isNullOrBlank()) {
            response.setHeader(GUEST_ACCESS_TOKEN_HEADER, guestAccessToken)
        }
        return updated.asResponse()
    }


    @DeleteMapping(value = ["/events/signups/{id}"])
    @PreAuthorize(
        "hasPermission(#id, 'EventSignUp', 'delete') " +
                "or T(org.springframework.util.StringUtils).hasText(#guestAccessToken)"
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteEventSignup(
        @PathVariable id: Long,
        @RequestHeader(name = GUEST_ACCESS_TOKEN_HEADER, required = false) guestAccessToken: String?
    ) {
        useCases.delete(eventSignUpId = id, accessToken = guestAccessToken)
    }
}
