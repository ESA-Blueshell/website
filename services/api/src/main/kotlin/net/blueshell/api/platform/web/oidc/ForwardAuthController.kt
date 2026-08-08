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
 * protected admin IngressRoute (vault, headlamp, stalwart, traefik).
 * The middleware sends an authenticated GET to this endpoint
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
        // CR/LF-stripped copy of the attacker-controllable host header for logging (log-injection, #464).
        val safeHost = forwardedHost.replace('\r', '_').replace('\n', '_')
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
     * Returns true only when [host] is one of the known service hostnames in
     * [HOST_ROLE]. Comparison is case-insensitive to match the lookup above.
     *
     * This is the allowlist guard for the `?redirect=` parameter: we only embed
     * a post-login redirect URL when the target host is a host we explicitly
     * manage, preventing open-redirect exploitation via a forged X-Forwarded-Host.
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
