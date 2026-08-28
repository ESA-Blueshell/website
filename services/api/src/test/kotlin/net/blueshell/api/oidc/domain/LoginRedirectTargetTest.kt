package net.blueshell.api.oidc.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The frontend navigates to whatever the `redirect` parameter holds, so only a path on this
 * site may go in it. Anything that could leave the origin is replaced by the default landing
 * path rather than passed through (CWE-601).
 */
class LoginRedirectTargetTest {

    @Test
    fun `a protected page comes back with the api prefix Traefik stripped`() {
        assertThat(loginRedirectTarget("/oauth2/authorize", null))
            .isEqualTo("/api/oauth2/authorize")
    }

    @Test
    fun `the query string is carried so the flow resumes where it left off`() {
        assertThat(loginRedirectTarget("/oauth2/authorize", "client_id=vault&scope=openid"))
            .isEqualTo("/api/oauth2/authorize?client_id=vault&scope=openid")
    }

    @Test
    fun `an empty query string does not leave a trailing question mark`() {
        assertThat(loginRedirectTarget("/oauth2/authorize", "")).isEqualTo("/api/oauth2/authorize")
    }

    @Test
    fun `a protocol-relative request uri cannot send a member to another host`() {
        // "/api" + "//evil.example" is still protocol-relative once the browser resolves it.
        assertThat(loginRedirectTarget("//evil.example/phish", null)).isEqualTo(DEFAULT_POST_LOGIN_PATH)
    }

    @Test
    fun `a backslash protocol-relative form is rejected too`() {
        assertThat(loginRedirectTarget("/\\evil.example/phish", null)).isEqualTo(DEFAULT_POST_LOGIN_PATH)
    }

    @Test
    fun `an absolute url in the request uri is rejected`() {
        assertThat(loginRedirectTarget("https://evil.example/phish", null))
            .isEqualTo(DEFAULT_POST_LOGIN_PATH)
    }

    @Test
    fun `a request uri not rooted at a slash is rejected`() {
        assertThat(loginRedirectTarget("evil.example", null)).isEqualTo(DEFAULT_POST_LOGIN_PATH)
    }

    @Test
    fun `a query string cannot smuggle a line break into the parameter`() {
        val target = loginRedirectTarget("/oauth2/authorize", "next=%0d%0aSet-Cookie:+x")

        assertThat(target).doesNotContain("\r").doesNotContain("\n")
    }
}
