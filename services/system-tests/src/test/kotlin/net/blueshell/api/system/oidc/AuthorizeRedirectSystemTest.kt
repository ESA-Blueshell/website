package net.blueshell.api.system.oidc

import net.blueshell.systemtests.TestHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Drives /oauth2/authorize for the registered downstream clients
 * (headlamp + vault) over real HTTP. Validates:
 *   - anonymous request → 302 to /login with the original URL preserved
 *   - non-admin authenticated request to admin-only client → 403 from the
 *     downstream-client gating filter (see AuthorizationServerConfig)
 *   - admin authenticated request → SAS handles authorize (302 to the
 *     client's redirect_uri with `code=...`)
 *
 * Mirrors personal-stack's GrafanaOidcSystemTest / DownstreamOidc-
 * AuthorizationSystemTest but parametrized over the two clients this
 * project actually has.
 */
@Tag("system")
class AuthorizeRedirectSystemTest : OidcSystemTestBase() {

    private fun authorizeUrl(clientId: String, pkce: OidcTestHelper.Pkce?, redirect: String): String {
        val params = buildList {
            add("response_type=code")
            add("client_id=$clientId")
            add("scope=${urlEncode("openid profile email groups")}")
            add("redirect_uri=${urlEncode(redirect)}")
            add("state=test-state")
            if (pkce != null) {
                add("code_challenge=${pkce.challenge}")
                add("code_challenge_method=${pkce.method}")
            }
        }.joinToString("&")
        return "/oauth2/authorize?$params"
    }

    @Test
    fun `anonymous authorize for headlamp redirects to login with original URL preserved`() {
        val pkce = OidcTestHelper.newPkce()
        val redirect = "https://headlamp.esa-blueshell.nl/oidc-callback"

        val response = get(authorizeUrl("headlamp", pkce, redirect))

        assertThat(response.statusCode()).isEqualTo(302)
        val location = response.headers().firstValue("Location").orElse("")
        // Tomcat resolves the relative path to absolute, so match on the
        // `/login?redirect=` segment instead of asserting it starts the URL.
        assertThat(location).contains("/login?redirect=")
        assertThat(location).contains(urlEncode("/oauth2/authorize"))
        assertThat(location).contains(urlEncode("client_id=headlamp"))
    }

    @Test
    fun `anonymous authorize for vault redirects to login`() {
        val redirect = "https://vault.esa-blueshell.nl/ui/vault/auth/oidc/oidc/callback"

        val response = get(authorizeUrl("vault", pkce = null, redirect = redirect))

        assertThat(response.statusCode()).isEqualTo(302)
        val location = response.headers().firstValue("Location").orElse("")
        assertThat(location).contains("/login?redirect=")
        assertThat(location).contains(urlEncode("client_id=vault"))
    }

    @Test
    fun `member hitting admin-only headlamp authorize is blocked by 403`() {
        val member = TestHelper.registerActivateAndPromote("MEMBER")
        val pkce = OidcTestHelper.newPkce()
        val redirect = "https://headlamp.esa-blueshell.nl/oidc-callback"

        val response = get(
            authorizeUrl("headlamp", pkce, redirect),
            sessionToken = sessionTokenFor(member),
        )

        assertThat(response.statusCode()).isEqualTo(403)
    }

    @Test
    fun `member hitting admin-only vault authorize is blocked by 403`() {
        val member = TestHelper.registerActivateAndPromote("MEMBER")
        val redirect = "https://vault.esa-blueshell.nl/ui/vault/auth/oidc/oidc/callback"

        val response = get(
            authorizeUrl("vault", pkce = null, redirect = redirect),
            sessionToken = sessionTokenFor(member),
        )

        assertThat(response.statusCode()).isEqualTo(403)
    }

    @Test
    fun `admin authorize for headlamp redirects to client redirect_uri with code`() {
        val admin = TestHelper.registerActivateAndPromote("ADMIN")
        val pkce = OidcTestHelper.newPkce()
        val redirect = "https://headlamp.esa-blueshell.nl/oidc-callback"

        val response = get(
            authorizeUrl("headlamp", pkce, redirect),
            sessionToken = sessionTokenFor(admin),
        )

        assertThat(response.statusCode()).isEqualTo(302)
        val location = response.headers().firstValue("Location").orElse("")
        assertThat(location).startsWith(redirect)
        assertThat(OidcTestHelper.queryParam(location, "code")).isNotBlank()
        assertThat(OidcTestHelper.queryParam(location, "state")).isEqualTo("test-state")
    }

    @Test
    fun `admin authorize for vault redirects to client redirect_uri with code`() {
        val admin = TestHelper.registerActivateAndPromote("ADMIN")
        val redirect = "https://vault.esa-blueshell.nl/ui/vault/auth/oidc/oidc/callback"

        val response = get(
            authorizeUrl("vault", pkce = null, redirect = redirect),
            sessionToken = sessionTokenFor(admin),
        )

        assertThat(response.statusCode()).isEqualTo(302)
        val location = response.headers().firstValue("Location").orElse("")
        assertThat(location).startsWith(redirect)
        assertThat(OidcTestHelper.queryParam(location, "code")).isNotBlank()
        assertThat(OidcTestHelper.queryParam(location, "state")).isEqualTo("test-state")
    }
}
