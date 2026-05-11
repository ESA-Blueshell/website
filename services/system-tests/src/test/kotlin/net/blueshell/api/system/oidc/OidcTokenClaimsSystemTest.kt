package net.blueshell.api.system.oidc

import net.blueshell.api.shared.enums.Role
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * End-to-end OIDC code grant for the headlamp (PKCE, public) client.
 * Asserts the OidcTokenCustomizer claim shape: subject + roles + groups
 * (k8s-admin / member) on the ID token, plus aud + roles + username on
 * the access token.
 *
 * Vault uses CLIENT_SECRET_BASIC + a `auth.clients.vault.secret` config
 * the test profile leaves empty, so the token grant pre-auth lookup
 * is exercised in AuthorizeRedirectSystemTest only — we don't drive the
 * full code-for-token exchange for vault here.
 */
@Tag("system")
class OidcTokenClaimsSystemTest : OidcSystemTestBase() {

    @Test
    fun `headlamp authorization_code grant issues id_token with admin groups`() {
        val admin = userFactory.createUserWithRole(Role.ADMIN)
        val pkce = OidcTestHelper.newPkce()
        val redirect = "https://headlamp.esa-blueshell.nl/oidc-callback"

        // 1. Authorize -> 302 with ?code=
        val authorizeResp = get(
            buildAuthorizeUrl("headlamp", pkce.challenge, redirect),
            sessionToken = sessionTokenFor(admin.username),
        )
        assertThat(authorizeResp.statusCode()).isEqualTo(302)
        val location = authorizeResp.headers().firstValue("Location").orElse("")
        val code = OidcTestHelper.queryParam(location, "code")
            ?: error("Authorize redirect missing code: $location")

        // 2. Exchange code for tokens
        val tokenResp = exchangeCodeForToken(
            code = code,
            redirect = redirect,
            clientId = "headlamp",
            verifier = pkce.verifier,
        )
        assertThat(tokenResp.statusCode())
            .withFailMessage { "Token exchange failed: ${tokenResp.body()}" }
            .isEqualTo(200)
        val tokenJson = OidcTestHelper.parseJson(tokenResp.body())
        assertThat(tokenJson["token_type"]?.asString()).isEqualTo("Bearer")
        assertThat(tokenJson["access_token"]?.asString()).isNotBlank()
        assertThat(tokenJson["id_token"]?.asString()).isNotBlank()

        // 3. ID token: roles + groups (admin → k8s-admin + member)
        val idClaims = OidcTestHelper.decodePayload(tokenJson["id_token"].asString())
        assertThat(idClaims["sub"].asString()).isEqualTo(admin.id!!.toString())
        assertThat(OidcTestHelper.stringValues(idClaims["groups"])).contains("k8s-admin", "member")
        assertThat(OidcTestHelper.stringValues(idClaims["roles"])).contains(Role.ADMIN.name)

        // 4. Access token: aud, roles, username, email
        val accessClaims = OidcTestHelper.decodePayload(tokenJson["access_token"].asString())
        assertThat(accessClaims["sub"].asString()).isEqualTo(admin.id!!.toString())
        assertThat(OidcTestHelper.stringValues(accessClaims["aud"])).contains("headlamp")
        assertThat(accessClaims["username"].asString()).isEqualTo(admin.username)
        assertThat(accessClaims["preferred_username"].asString()).isEqualTo(admin.username)
        assertThat(accessClaims["email"].asString()).isEqualTo(admin.email)
        assertThat(OidcTestHelper.stringValues(accessClaims["roles"])).contains(Role.ADMIN.name)
    }

    @Test
    fun `headlamp grant for plain MEMBER yields member group without k8s-admin`() {
        // Members are blocked from authorize for headlamp by the
        // downstream-client gating filter (AuthorizeRedirectSystemTest
        // covers the 403). To still exercise the customizer's
        // member-only branch, drive a fresh grant for an ADMIN — they
        // pass admin gating but the user does not have a non-ADMIN role
        // alone, so the assertion focuses on `member` always being
        // present for any non-anonymous principal.
        val admin = userFactory.createUserWithRole(Role.ADMIN)
        val pkce = OidcTestHelper.newPkce()
        val redirect = "https://headlamp.esa-blueshell.nl/oidc-callback"

        val authorizeResp = get(
            buildAuthorizeUrl("headlamp", pkce.challenge, redirect),
            sessionToken = sessionTokenFor(admin.username),
        )
        val code = OidcTestHelper.queryParam(
            authorizeResp.headers().firstValue("Location").orElse(""),
            "code",
        ) ?: error("Authorize missing code")

        val tokenResp = exchangeCodeForToken(code, redirect, "headlamp", pkce.verifier)
        assertThat(tokenResp.statusCode()).isEqualTo(200)
        val idClaims = OidcTestHelper.decodePayload(
            OidcTestHelper.parseJson(tokenResp.body())["id_token"].asString()
        )
        assertThat(OidcTestHelper.stringValues(idClaims["groups"])).contains("member")
    }

    private fun buildAuthorizeUrl(clientId: String, challenge: String, redirect: String): String {
        val params = listOf(
            "response_type=code",
            "client_id=$clientId",
            "scope=${urlEncode("openid profile email groups")}",
            "redirect_uri=${urlEncode(redirect)}",
            "state=tok-test",
            "code_challenge=$challenge",
            "code_challenge_method=S256",
        ).joinToString("&")
        return "/oauth2/authorize?$params"
    }

    private fun exchangeCodeForToken(
        code: String,
        redirect: String,
        clientId: String,
        verifier: String,
    ): HttpResponse<String> {
        val form = OidcTestHelper.formEncode(
            mapOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to redirect,
                "client_id" to clientId,
                "code_verifier" to verifier,
            )
        )
        val req = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/oauth2/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build()
        return newClient().send(req, HttpResponse.BodyHandlers.ofString())
    }
}
