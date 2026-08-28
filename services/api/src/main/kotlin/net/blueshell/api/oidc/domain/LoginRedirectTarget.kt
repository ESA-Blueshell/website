package net.blueshell.api.oidc.domain

/**
 * Decides what goes in `/login?redirect=…` when a request reaches the authorization server
 * unauthenticated. The frontend navigates to whatever that parameter holds, so it may only
 * ever name a path on this site (CWE-601).
 */
object LoginRedirectTarget {

    /** Where a member lands when the page they wanted is not safe to return to. */
    const val DEFAULT_PATH = "/"

    /**
     * Traefik strips `/api` before forwarding, so it is re-added: the redirect has to name the
     * public URL for the frontend's off-SPA navigation to re-enter this chain.
     */
    fun forRequest(requestUri: String, queryString: String?): String {
        val publicPath = "/api$requestUri"
        val composed = if (!queryString.isNullOrEmpty()) "$publicPath?$queryString" else publicPath
        return sameOriginOrDefault(composed)
    }

    /**
     * The last word on what may be handed to the frontend to navigate to.
     *
     * Composing a target from a servlet's request URI cannot currently produce anything but a
     * same-origin path, since the container normalises it and this prepends `/api`. This is the
     * check that has to keep holding if either of those ever stops being true, so it guards the
     * composed value rather than the input it was built from.
     */
    fun sameOriginOrDefault(candidate: String): String =
        if (isSameOriginPath(candidate)) candidate else DEFAULT_PATH

    // `//host` and `/\host` are protocol-relative: a browser resolves both to another origin.
    private fun isSameOriginPath(value: String): Boolean =
        value.startsWith("/") && !value.startsWith("//") && !value.startsWith("/\\")
}
