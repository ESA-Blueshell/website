package net.blueshell.api.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import net.blueshell.api.auth.api.TokenGenerator
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Re-issues the auth cookie on a request made with a token part-way through its life.
 *
 * A sign-in has one length, and two things carry it: the token in the auth cookie and the http
 * session beside it. The session slides — every authenticated request writes the security context
 * back to it and Valkey's key moves out again — while the token is minted once at sign-in. Left
 * alone the two end the same sign-in on different days: a reader on the site daily would still be
 * turned away thirty days after they last typed their password. So the token slides too, on a
 * request that finds it old enough to be worth replacing.
 *
 * Re-issuing costs one signature and one `Set-Cookie`, so it is not done per request: `renewAfter`
 * decides how much of the life passes first, and at the default that is once a day per browser.
 *
 * The new token carries a new `jti`. Revoking the old one no longer reaches the reader, which is
 * why logout clears the cookie and drops the session as well as revoking — see
 * `AuthenticationController.logout`.
 */
@Component
class AuthTokenRenewalService(
    private val tokenGenerator: TokenGenerator,
    private val authTokenCookieService: AuthTokenCookieService,
    @param:Value($$"${app.jwt.renew-after}") private val renewAfter: Duration
) {

    /** Paths that issue or retire the cookie themselves, and must not have one written over them. */
    private val excludedPaths = setOf("/auth", "/auth/logout")

    fun renewIfDue(
        request: HttpServletRequest,
        response: HttpServletResponse,
        username: String,
        expiresAtEpochMs: Long?
    ) {
        if (request.requestURI in excludedPaths) return
        if (response.isCommitted) return
        if (expiresAtEpochMs == null) return

        val lifetimeMs = tokenGenerator.expirationMs
        val ageMs = lifetimeMs - (expiresAtEpochMs - System.currentTimeMillis())
        if (ageMs < renewAfter.toMillis()) return

        val token = tokenGenerator.generateToken(username)
        authTokenCookieService.writeAuthCookie(response, token, lifetimeMs)
    }
}
