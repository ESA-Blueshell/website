package net.blueshell.api.security

/**
 * One rule for the `Secure` flag, read by all three cookies this application sets:
 * the auth cookie ([AuthTokenCookieService]), the session cookie (`SessionConfig`)
 * and the CSRF cookie (`SecurityConfig`).
 *
 * `SameSite=None` is dropped by every browser unless the cookie is also `Secure`, so
 * a cookie asking for None is Secure whatever the environment says about https. The
 * converse is what makes this a rule rather than a constant: a browser rejects a
 * Secure cookie on an insecure origin, exempting only `localhost` and `127.0.0.1`.
 * Development is reached from a LAN address too -- a phone on the same network -- so
 * it asks for Lax and its cookies arrive without Secure.
 *
 * Production needs None on the auth and session cookies for a different reason: they
 * travel to the vault, headlamp and traefik subdomains, where Traefik's forwardAuth
 * reads them. Each cookie keeps its own property so that reason stays separable.
 */
object CookieFlags {
    /** What a cookie asks for when nothing is configured. */
    const val DEFAULT_SAME_SITE: String = "None"

    fun sameSite(configured: String): String =
        configured.trim().ifEmpty { DEFAULT_SAME_SITE }

    fun secure(requireHttps: Boolean, sameSite: String): Boolean =
        requireHttps || sameSite.equals(DEFAULT_SAME_SITE, ignoreCase = true)
}
