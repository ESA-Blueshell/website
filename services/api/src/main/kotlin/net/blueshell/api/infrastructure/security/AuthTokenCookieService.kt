package net.blueshell.api.infrastructure.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class AuthTokenCookieService(
    @param:Value($$"${security.auth-cookie.name:BSH_AUTH}")
    private val cookieName: String,
    @param:Value($$"${security.auth-cookie.path:/}")
    private val cookiePath: String,
    @param:Value($$"${security.auth-cookie.same-site:None}")
    private val sameSite: String,
    // Empty default → no `Domain` attribute → host-only cookie (the
    // browser only sends it back to the exact host that set it). Set
    // explicitly in production to e.g. `.esa-blueshell.nl` so the cookie
    // is sent to every subdomain (vault, headlamp, traefik, stalwart)
    // — Traefik's forwardAuth needs the cookie to authenticate
    // the request. Local dev uses an empty domain so the cookie still
    // works against `localhost` / `127.0.0.1`.
    @param:Value($$"${security.auth-cookie.domain:}")
    private val cookieDomain: String,
    @param:Value($$"${app.security.require-https:true}")
    private val requireHttps: Boolean
) {
    private val effectiveSameSite: String = resolveSameSite(sameSite)
    private val effectiveSecure: Boolean =
        requireHttps || effectiveSameSite.equals("None", ignoreCase = true)
    private val effectiveDomain: String? = cookieDomain.trim().takeIf { it.isNotEmpty() }

    fun writeAuthCookie(
        response: HttpServletResponse,
        token: String,
        ttlMillis: Long
    ) {
        val maxAgeSeconds = (ttlMillis / 1000).coerceAtLeast(0)
        val cookie = ResponseCookie.from(cookieName, token)
            .httpOnly(true)
            .secure(effectiveSecure)
            .path(cookiePath)
            .sameSite(effectiveSameSite)
            .maxAge(Duration.ofSeconds(maxAgeSeconds))
            .also { if (effectiveDomain != null) it.domain(effectiveDomain) }
            .build()
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
    }

    fun clearAuthCookie(response: HttpServletResponse) {
        val cookie = ResponseCookie.from(cookieName, "")
            .httpOnly(true)
            .secure(effectiveSecure)
            .path(cookiePath)
            .sameSite(effectiveSameSite)
            .maxAge(Duration.ZERO)
            .also { if (effectiveDomain != null) it.domain(effectiveDomain) }
            .build()
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
    }

    fun resolveToken(request: HttpServletRequest): String? {
        return request.cookies
            ?.firstOrNull { it.name == cookieName }
            ?.value
            ?.takeIf { it.isNotBlank() }
    }

    private fun resolveSameSite(configuredSameSite: String): String {
        val trimmed = configuredSameSite.trim()
        return if (trimmed.isEmpty()) "None" else trimmed
    }
}
