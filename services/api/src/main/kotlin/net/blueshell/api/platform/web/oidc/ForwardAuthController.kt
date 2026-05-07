package net.blueshell.api.platform.web.oidc

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.servlet.http.HttpServletRequest
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.UserPrincipal
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Called by the Traefik ForwardAuth middleware for every request to a
 * protected admin IngressRoute (vault, headlamp, listmonk, stalwart,
 * traefik). The middleware sends an authenticated GET to this endpoint
 * with the original request's `X-Forwarded-Host` / `X-Forwarded-Uri`
 * headers and forwards back any `X-User-Id` / `X-User-Groups` headers
 * we set. Traefik's contract is "2xx → forward to the backing service,
 * everything else → return verbatim to the client" — that includes
 * 3xx, so we use 302 redirects to steer anonymous and underprivileged
 * users to friendly pages on the SPA instead of a raw 401.
 *
 *   anonymous              → 302 to /login?redirect=<original-url>
 *   authenticated, wrong   → 302 to /unauthorized?service=<host>
 *   authenticated, right   → 200 + X-User-Id / X-User-Groups
 *
 * The host→required-role table is duplicated by `MyServicesController`
 * in the same package so the catalog the user sees on /myapps reflects
 * what they can actually reach. Five entries today; a future PR can
 * lift them to config.
 */
@RestController
@Tag(name = "Forward Auth")
@RequestMapping("/oauth2/forward-auth")
class ForwardAuthController(
    @param:Value($$"${forward-auth.frontend-base-url:https://v2.esa-blueshell.nl}")
    private val frontendBaseUrl: String,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping
    @PermitAll
    fun forwardAuth(
        @AuthenticationPrincipal principal: UserPrincipal?,
        request: HttpServletRequest,
    ): ResponseEntity<Void> {
        val forwardedHost = request.getHeader("X-Forwarded-Host").orEmpty()
        val forwardedUri = request.getHeader("X-Forwarded-Uri").orEmpty().ifEmpty { "/" }
        val forwardedProto = request.getHeader("X-Forwarded-Proto").orEmpty().ifEmpty { "https" }
        val originalUrl = "$forwardedProto://$forwardedHost$forwardedUri"

        val required = HOST_ROLE[forwardedHost.lowercase()] ?: run {
            // Fail-closed: an unknown host (mis-configured IngressRoute, or
            // someone pointing forward-auth at us via Host injection) gets
            // ADMIN-required. Warn so the operator notices.
            log.warn("forward-auth: unknown host '{}' — defaulting to ADMIN", forwardedHost)
            Role.ADMIN
        }

        if (principal == null) {
            return redirect("$frontendBaseUrl/login?redirect=${urlEncode(originalUrl)}")
        }
        if (!principal.hasAuthority(required)) {
            return redirect("$frontendBaseUrl/unauthorized?service=${urlEncode(forwardedHost)}")
        }
        return ResponseEntity.ok()
            .header("X-User-Id", principal.id.toString())
            .header(
                "X-User-Groups",
                principal.roles.flatMap { it.allInheritedRoles }.map { it.name }.joinToString(","),
            )
            .build()
    }

    private fun redirect(location: String): ResponseEntity<Void> =
        ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build()

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)

    companion object {
        // Keep in lockstep with MyServicesController.kt's visibility filter
        // — same five entries, same role gates.
        val HOST_ROLE: Map<String, Role> = mapOf(
            "traefik.esa-blueshell.nl"  to Role.ADMIN,
            "vault.esa-blueshell.nl"    to Role.ADMIN,
            "headlamp.esa-blueshell.nl" to Role.ADMIN,
            "listmonk.esa-blueshell.nl" to Role.BOARD,
            "stalwart.esa-blueshell.nl" to Role.BOARD,
        )
    }
}
