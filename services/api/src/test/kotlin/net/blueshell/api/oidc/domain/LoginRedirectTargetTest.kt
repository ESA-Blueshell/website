package net.blueshell.api.oidc.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LoginRedirectTargetTest {

    @Test
    fun `a protected page comes back with the api prefix Traefik stripped`() {
        assertThat(LoginRedirectTarget.forRequest("/oauth2/authorize", null))
            .isEqualTo("/api/oauth2/authorize")
    }

    @Test
    fun `the query string is carried so the flow resumes where it left off`() {
        assertThat(LoginRedirectTarget.forRequest("/oauth2/authorize", "client_id=vault&scope=openid"))
            .isEqualTo("/api/oauth2/authorize?client_id=vault&scope=openid")
    }

    @Test
    fun `an empty query string does not leave a trailing question mark`() {
        assertThat(LoginRedirectTarget.forRequest("/oauth2/authorize", ""))
            .isEqualTo("/api/oauth2/authorize")
    }

    @Test
    fun `a doubled slash in the request uri stays on this site once it is prefixed`() {
        // Worth stating rather than assuming: prefixing makes this a path under /api, not a
        // protocol-relative URL, so it is kept rather than discarded.
        assertThat(LoginRedirectTarget.forRequest("//headlamp.esa-blueshell.nl/x", null))
            .isEqualTo("/api//headlamp.esa-blueshell.nl/x")
    }

    @Test
    fun `a protocol-relative target would send a member to another host`() {
        assertThat(LoginRedirectTarget.sameOriginOrDefault("//evil.example/phish"))
            .isEqualTo(LoginRedirectTarget.DEFAULT_PATH)
    }

    @Test
    fun `a backslash protocol-relative target is rejected too`() {
        assertThat(LoginRedirectTarget.sameOriginOrDefault("/\\evil.example/phish"))
            .isEqualTo(LoginRedirectTarget.DEFAULT_PATH)
    }

    @Test
    fun `an absolute url target is rejected`() {
        assertThat(LoginRedirectTarget.sameOriginOrDefault("https://evil.example/phish"))
            .isEqualTo(LoginRedirectTarget.DEFAULT_PATH)
    }

    @Test
    fun `a target not rooted at a slash is rejected`() {
        assertThat(LoginRedirectTarget.sameOriginOrDefault("evil.example")).isEqualTo(LoginRedirectTarget.DEFAULT_PATH)
    }

    @Test
    fun `a same-origin path is handed through unchanged`() {
        assertThat(LoginRedirectTarget.sameOriginOrDefault("/api/oauth2/authorize?scope=openid"))
            .isEqualTo("/api/oauth2/authorize?scope=openid")
    }
}
