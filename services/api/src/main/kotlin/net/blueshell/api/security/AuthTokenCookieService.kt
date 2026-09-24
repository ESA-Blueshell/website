package net.blueshell.api.security

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
    // is sent to every subdomain (vault, headlamp, traefik,
    // stalwart) — Traefik's forwardAuth needs the cookie to authenticate
    // the request. Local dev uses an empty domain so the cookie still
    // works against `localhost` / `127.0.0.1`.
    @param:Value($$"${security.auth-cookie.domain:}")
    private val cookieDomain: String,
    @param:Value($$"${app.security.require-https:true}")
    private val requireHttps: Boolean,
) {
    private val effectiveSameSite: String = CookieFlags.sameSite(sameSite)
    private val effectiveSecure: Boolean = CookieFlags.secure(requireHttps, effectiveSameSite)
    private val effectiveDomain: String? = cookieDomain.trim().takeIf { it.isNotEmpty() }

    fun writeAuthCookie(
        response: HttpServletResponse,
        token: String,
        ttlMillis: Long,
    ) = writeCookie(response, cookieName, token, ttlMillis)

    fun clearAuthCookie(response: HttpServletResponse) = clearCookie(response, cookieName)

    fun resolveToken(request: HttpServletRequest): String? = resolveCookie(request, cookieName)

    /** An http-only cookie with the auth cookie's attributes, for the challenge and a trusted browser. */
    fun writeCookie(
        response: HttpServletResponse,
        name: String,
        value: String,
        ttlMillis: Long,
    ) {
        val cookie =
            ResponseCookie
                .from(name, value)
                .httpOnly(true)
                .secure(effectiveSecure)
                .path(cookiePath)
                .sameSite(effectiveSameSite)
                .maxAge(Duration.ofSeconds((ttlMillis / 1000).coerceAtLeast(0)))
                .also { if (effectiveDomain != null) it.domain(effectiveDomain) }
                .build()
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
    }

    fun clearCookie(
        response: HttpServletResponse,
        name: String,
    ) = writeCookie(response, name, "", 0)

    fun resolveCookie(
        request: HttpServletRequest,
        name: String,
    ): String? =
        request.cookies
            ?.firstOrNull { it.name == name }
            ?.value
            ?.takeIf { it.isNotBlank() }
}
