package net.blueshell.api.security

/**
 * One rule for the `Secure` flag, read by the auth, session and CSRF cookies alike.
 *
 * A cookie asking for `SameSite=None` is Secure whatever the environment says, since browsers
 * drop None without it. The converse makes this a rule rather than a constant: a browser
 * rejects a Secure cookie on an insecure origin except on localhost, and development is reached
 * from a LAN address too, so it asks for Lax and arrives without Secure. Production needs None
 * on the auth and session cookies, which travel to the admin subdomains for Traefik's
 * forwardAuth; each keeps its own property so that reason stays separable.
 */
object CookieFlags {
    /** What a cookie asks for when nothing is configured. */
    const val DEFAULT_SAME_SITE: String = "None"

    fun sameSite(configured: String): String =
        configured.trim().ifEmpty { DEFAULT_SAME_SITE }

    fun secure(requireHttps: Boolean, sameSite: String): Boolean =
        requireHttps || sameSite.equals(DEFAULT_SAME_SITE, ignoreCase = true)
}
