package net.blueshell.api.oidc.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.servlet.http.HttpServletRequest
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.UserPrincipal
import net.blueshell.api.shared.util.sanitizeForLog
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
 * Answers Traefik's ForwardAuth middleware for every request to a protected admin route.
 *
 * Traefik forwards a 2xx to the backing service and returns anything else to the client
 * verbatim, 3xx included, which is what lets a redirect steer a user to the SPA over a raw 401:
 *
 *   anonymous              → 302 to /login?redirect=<original-url>
 *   authenticated, wrong   → 302 to /unauthorized?service=<host>
 *   authenticated, right   → 200 + X-User-Id / X-User-Groups
 *
 * `MyServicesController` mirrors the host-to-role table; both must change together.
 */
@RestController
@Tag(name = "Forward Auth")
@RequestMapping("/oauth2/forward-auth")
class ForwardAuthController(
    @param:Value($$"${forward-auth.frontend-base-url:https://esa-blueshell.nl}")
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
        // Scrubbed copy of the attacker-controllable host header, for logging only.
        val safeHost = sanitizeForLog(forwardedHost)
        val forwardedUri = request.getHeader("X-Forwarded-Uri").orEmpty().ifEmpty { "/" }
        val forwardedProto = request.getHeader("X-Forwarded-Proto").orEmpty().ifEmpty { "https" }
        val originalUrl = "$forwardedProto://$forwardedHost$forwardedUri"

        val required = HOST_ROLE[forwardedHost.lowercase()] ?: run {
            // Fail-closed: an unknown host (mis-configured IngressRoute, or
            // someone pointing forward-auth at us via Host injection) gets
            // ADMIN-required. Warn so the operator notices.
            log.warn("forward-auth: unknown host '{}' — defaulting to ADMIN", safeHost)
            Role.ADMIN
        }

        // SPAs (Stalwart webadmin, Headlamp, …) fetch their own /api/* via XHR.
        // A 302 to esa-blueshell.nl/login auto-follows cross-origin and the
        // browser blocks it with CORS, surfacing as a generic network error in
        // the SPA. 401 lets the SPA show a proper "session expired" state.
        val wantsHtml = request.getHeader(HttpHeaders.ACCEPT).orEmpty().contains("text/html")

        if (principal == null) {
            return if (wantsHtml) {
                // Validate the redirect target against the allowlist of known service
                // hosts before embedding it in the login redirect. An attacker who
                // controls the X-Forwarded-Host header must not be able to steer
                // a victim to an arbitrary external URL (CWE-601 / CodeQL #471).
                val safeRedirectParam = if (isSafeRedirectTarget(forwardedHost)) {
                    "?redirect=${urlEncode(originalUrl)}"
                } else {
                    log.warn(
                        "forward-auth: rejecting redirect to untrusted host '{}' — omitting redirect param",
                        safeHost,
                    )
                    ""
                }
                redirect("$frontendBaseUrl/login$safeRedirectParam")
            } else {
                ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.WWW_AUTHENTICATE, """Bearer realm="$frontendBaseUrl/login"""")
                    .build()
            }
        }
        if (!principal.hasAuthority(required)) {
            return if (wantsHtml) {
                redirect("$frontendBaseUrl/unauthorized?service=${urlEncode(forwardedHost)}")
            } else {
                ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            }
        }
        return ResponseEntity.ok()
            .header("X-User-Id", principal.id.toString())
            .header(
                "X-User-Groups",
                principal.roles.flatMap { it.allInheritedRoles }.map { it.name }.joinToString(","),
            )
            .build()
    }

    /**
     * The allowlist guard for `?redirect=`: a post-login redirect is embedded only for a host in
     * [HOST_ROLE], so a forged `X-Forwarded-Host` cannot turn this into an open redirect.
     */
    internal fun isSafeRedirectTarget(host: String): Boolean =
        host.lowercase() in HOST_ROLE

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
            "stalwart.esa-blueshell.nl" to Role.BOARD,
        )
    }
}
