package net.blueshell.api.platform.config

import jakarta.annotation.security.PermitAll
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.mvc.method.RequestMappingInfo

/**
 * What anybody may read without logging in: every GET a controller method opens with `@PermitAll`,
 * read off the handler mappings at startup, and [AnonymousReads.OPENED_ELSEWHERE].
 */
internal object AnonymousReads {
    /** Anonymous reads that no `@PermitAll` handler names, and why each is open anyway. */
    val OPENED_ELSEWHERE =
        mapOf(
            "/events/{id}" to "method security lets a visitor read an approved event",
            "/events/{id}/link-preview" to "method security lets a visitor read an approved event, as Discord's embed does",
            "/events/{id}/roster" to "method security lets a visitor read who signed up for an event they may read",
            "/events/{eventId}/banners" to "method security lets a visitor read an approved event's banner",
            "/events/signups/byAccessToken" to "a guest has no login; method security checks the access token sent",
            "/committees/{committeeId}" to "method security answers a visitor the committee's summary",
            "/discord/live/socket" to "the websocket the Discord band follows, which opens with a GET and has no controller",
        )

    fun of(handlers: Map<RequestMappingInfo, HandlerMethod>): List<String> = (permitAllReads(handlers) + OPENED_ELSEWHERE.keys).distinct()

    /** The GET paths a controller method opens to anybody with `@PermitAll`, as its mapping spells them. */
    fun permitAllReads(handlers: Map<RequestMappingInfo, HandlerMethod>): List<String> =
        handlers
            .filter { (info, handler) -> RequestMethod.GET in info.methodsCondition.methods && opensToAnybody(handler) }
            .flatMap { (info, _) -> info.patternValues }
            .distinct()
            .sorted()

    private fun opensToAnybody(handler: HandlerMethod): Boolean =
        handler.hasMethodAnnotation(PermitAll::class.java) ||
            (
                AnnotatedElementUtils.hasAnnotation(handler.beanType, PermitAll::class.java) &&
                    !handler.hasMethodAnnotation(PreAuthorize::class.java)
            )
}
