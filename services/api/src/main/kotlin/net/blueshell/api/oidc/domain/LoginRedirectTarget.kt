package net.blueshell.api.oidc.domain

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Decides what goes in `/login?redirect=…` when a request reaches the authorization server
 * unauthenticated. The frontend navigates to whatever that parameter holds, so it may only
 * ever name a path on this site (CWE-601).
 *
 * The target is rebuilt rather than copied: the path is a constant, and only the authorization
 * parameters named in [CARRIED_PARAMETERS] are carried, each URL-encoded. Nothing the caller
 * sends reaches the redirect unencoded, and no caller-supplied text decides the path.
 */
object LoginRedirectTarget {

    /** Where a member lands when the page they wanted is not one this can resume. */
    const val DEFAULT_PATH = "/"

    /**
     * The only endpoint on this chain a browser navigates to directly. The rest are called by
     * a client with a token, so there is nothing to send a member back to.
     */
    private const val AUTHORIZE_PATH = "/oauth2/authorize"

    /**
     * Traefik strips `/api` before forwarding, so it is re-added: the redirect has to name the
     * public URL for the frontend's off-SPA navigation to re-enter this chain.
     */
    private const val PUBLIC_AUTHORIZE_PATH = "/api$AUTHORIZE_PATH"

    /**
     * What an authorization request needs to resume after login. Dropping these strands the
     * flow: the request comes back with no client, no scope and no PKCE challenge.
     */
    private val CARRIED_PARAMETERS = listOf(
        "response_type",
        "client_id",
        "redirect_uri",
        "scope",
        "state",
        "nonce",
        "prompt",
        "login_hint",
        "code_challenge",
        "code_challenge_method",
    )

    fun forRequest(requestUri: String, parameter: (String) -> String?): String {
        if (requestUri != AUTHORIZE_PATH) return DEFAULT_PATH

        val query = CARRIED_PARAMETERS
            .mapNotNull { name -> parameter(name)?.let { "$name=${encode(it)}" } }
            .joinToString("&")

        return if (query.isEmpty()) PUBLIC_AUTHORIZE_PATH else "$PUBLIC_AUTHORIZE_PATH?$query"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
}
