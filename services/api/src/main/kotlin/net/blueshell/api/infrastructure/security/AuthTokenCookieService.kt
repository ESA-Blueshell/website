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
    @param:Value($$"${security.auth-cookie.domain:}")
    private val cookieDomain: String,
    @param:Value($$"${app.security.require-https:true}")
    private val requireHttps: Boolean
) {
    private val effectiveSameSite: String = resolveSameSite(sameSite)
    private val effectiveSecure: Boolean =
        requireHttps || effectiveSameSite.equals("None", ignoreCase = true)

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
            .apply { if (cookieDomain.isNotBlank()) domain(cookieDomain) }
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
            .apply { if (cookieDomain.isNotBlank()) domain(cookieDomain) }
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
