package net.blueshell.api.oidc.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LoginRedirectTargetTest {

    private fun params(vararg pairs: Pair<String, String>): (String) -> String? {
        val values = mapOf(*pairs)
        return { name -> values[name] }
    }

    @Test
    fun `an authorization request comes back with the api prefix Traefik stripped`() {
        assertThat(LoginRedirectTarget.forRequest("/oauth2/authorize", params()))
            .isEqualTo("/api/oauth2/authorize")
    }

    @Test
    fun `the parameters the flow needs to resume are carried`() {
        // Losing these strands the member at an authorization request with no client, no scope
        // and no challenge, which is a broken login rather than a safe one.
        val target = LoginRedirectTarget.forRequest(
            "/oauth2/authorize",
            params(
                "response_type" to "code",
                "client_id" to "headlamp",
                "scope" to "openid profile",
                "code_challenge" to "abc123",
                "code_challenge_method" to "S256",
            ),
        )

        assertThat(target).isEqualTo(
            "/api/oauth2/authorize?response_type=code&client_id=headlamp" +
                "&scope=openid+profile&code_challenge=abc123&code_challenge_method=S256",
        )
    }

    @Test
    fun `a client's redirect uri is carried encoded rather than raw`() {
        val target = LoginRedirectTarget.forRequest(
            "/oauth2/authorize",
            params("redirect_uri" to "https://headlamp.esa-blueshell.nl/oidc-callback"),
        )

        assertThat(target)
            .isEqualTo("/api/oauth2/authorize?redirect_uri=https%3A%2F%2Fheadlamp.esa-blueshell.nl%2Foidc-callback")
    }

    @Test
    fun `a parameter nobody named is not carried`() {
        val target = LoginRedirectTarget.forRequest(
            "/oauth2/authorize",
            params("client_id" to "vault", "next" to "//evil.example"),
        )

        assertThat(target).isEqualTo("/api/oauth2/authorize?client_id=vault")
    }

    @Test
    fun `a carried value cannot break out of the query it sits in`() {
        val target = LoginRedirectTarget.forRequest(
            "/oauth2/authorize",
            params("state" to "x&client_id=evil#/"),
        )

        assertThat(target).isEqualTo("/api/oauth2/authorize?state=x%26client_id%3Devil%23%2F")
    }

    @Test
    fun `any other endpoint on this chain has nothing to send a member back to`() {
        assertThat(LoginRedirectTarget.forRequest("/userinfo", params("client_id" to "vault")))
            .isEqualTo(LoginRedirectTarget.DEFAULT_PATH)
        assertThat(LoginRedirectTarget.forRequest("//evil.example/phish", params()))
            .isEqualTo(LoginRedirectTarget.DEFAULT_PATH)
        assertThat(LoginRedirectTarget.forRequest("https://evil.example/phish", params()))
            .isEqualTo(LoginRedirectTarget.DEFAULT_PATH)
    }
}
