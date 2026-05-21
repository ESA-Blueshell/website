package net.blueshell.api.system.oidc

import net.blueshell.systemtests.TestHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/**
 * System-test coverage for `/oauth2/forward-auth`. Parametrized across
 * every host the controller gates today (vault, headlamp, stalwart,
 * traefik) so a future host added to `HOST_ROLE` without a
 * matching system-test entry fails this suite.
 */
@Tag("system")
class ForwardAuthChainSystemTest : OidcSystemTestBase() {

    companion object {
        private const val FRONTEND_BASE = "https://esa-blueshell.nl"

        @JvmStatic
        fun adminGatedHosts(): Stream<Arguments> = Stream.of(
            Arguments.of("vault.esa-blueshell.nl"),
            Arguments.of("headlamp.esa-blueshell.nl"),
            Arguments.of("traefik.esa-blueshell.nl"),
        )

        @JvmStatic
        fun boardGatedHosts(): Stream<Arguments> = Stream.of(
            Arguments.of("stalwart.esa-blueshell.nl"),
        )

        @JvmStatic
        fun allGatedHosts(): Stream<Arguments> =
            Stream.concat(adminGatedHosts(), boardGatedHosts())
    }

    @ParameterizedTest(name = "anonymous → 302 to /login (host={0})")
    @MethodSource("allGatedHosts")
    fun anonymous_redirects_to_login(host: String) {
        val response = get(
            "/oauth2/forward-auth",
            headers = mapOf(
                "X-Forwarded-Host" to host,
                "X-Forwarded-Uri" to "/dashboard",
                "X-Forwarded-Proto" to "https",
            ),
        )

        assertThat(response.statusCode()).isEqualTo(302)
        val location = response.headers().firstValue("Location").orElse("")
        assertThat(location).startsWith("$FRONTEND_BASE/login?redirect=")
        assertThat(location).contains(urlEncode(host))
        assertThat(location).contains(urlEncode("/dashboard"))
    }

    @ParameterizedTest(name = "member → 302 /unauthorized (host={0})")
    @MethodSource("allGatedHosts")
    fun member_blocked_with_unauthorized_redirect(host: String) {
        val member = TestHelper.registerActivateAndPromote("MEMBER")

        val response = get(
            "/oauth2/forward-auth",
            sessionToken = sessionTokenFor(member),
            headers = mapOf("X-Forwarded-Host" to host, "X-Forwarded-Uri" to "/"),
        )

        assertThat(response.statusCode()).isEqualTo(302)
        val location = response.headers().firstValue("Location").orElse("")
        assertThat(location).isEqualTo("$FRONTEND_BASE/unauthorized?service=$host")
    }

    @ParameterizedTest(name = "admin → 200 with X-User-Id (host={0})")
    @MethodSource("allGatedHosts")
    fun admin_passes_every_host(host: String) {
        val admin = TestHelper.registerActivateAndPromote("ADMIN")
        val adminId = TestHelper.findUser(admin.username)!!.id

        val response = get(
            "/oauth2/forward-auth",
            sessionToken = sessionTokenFor(admin),
            headers = mapOf("X-Forwarded-Host" to host),
        )

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.headers().firstValue("X-User-Id").orElse(""))
            .isEqualTo(adminId.toString())
        assertThat(response.headers().firstValue("X-User-Groups").orElse(""))
            .contains("ADMIN")
    }

    @ParameterizedTest(name = "board → 200 (host={0}, board-gated)")
    @MethodSource("boardGatedHosts")
    fun board_passes_board_gated_hosts(host: String) {
        val board = TestHelper.registerActivateAndPromote("BOARD")

        val response = get(
            "/oauth2/forward-auth",
            sessionToken = sessionTokenFor(board),
            headers = mapOf("X-Forwarded-Host" to host),
        )

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.headers().firstValue("X-User-Groups").orElse("")).contains("BOARD")
    }

    @ParameterizedTest(name = "board → 302 /unauthorized (host={0}, admin-gated)")
    @MethodSource("adminGatedHosts")
    fun board_blocked_on_admin_gated_hosts(host: String) {
        val board = TestHelper.registerActivateAndPromote("BOARD")

        val response = get(
            "/oauth2/forward-auth",
            sessionToken = sessionTokenFor(board),
            headers = mapOf("X-Forwarded-Host" to host),
        )

        assertThat(response.statusCode()).isEqualTo(302)
        assertThat(response.headers().firstValue("Location").orElse(""))
            .isEqualTo("$FRONTEND_BASE/unauthorized?service=$host")
    }

    @ParameterizedTest(name = "anonymous XHR → 401 (host={0})")
    @MethodSource("allGatedHosts")
    fun anonymous_xhr_returns_401_not_302(host: String) {
        // SPAs fetch `/api/*` with `Accept: application/json`. A 302 to
        // the login page auto-follows and CORS-blocks; 401 lets the SPA
        // recognise an expired session.
        val response = java.net.http.HttpClient.newHttpClient().send(
            java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("$baseUrl/oauth2/forward-auth"))
                .GET()
                .header("Accept", "application/json")
                .header("X-Forwarded-Host", host)
                .header("X-Forwarded-Uri", "/api/principal")
                .header("X-Forwarded-Proto", "https")
                .build(),
            java.net.http.HttpResponse.BodyHandlers.discarding(),
        )

        assertThat(response.statusCode()).isEqualTo(401)
        assertThat(response.headers().firstValue("WWW-Authenticate").orElse(""))
            .startsWith("Bearer realm=")
    }

    @ParameterizedTest(name = "member XHR on board-gated → 403 (host={0})")
    @MethodSource("boardGatedHosts")
    fun member_xhr_on_board_host_returns_403(host: String) {
        val member = TestHelper.registerActivateAndPromote("MEMBER")

        val response = java.net.http.HttpClient.newHttpClient().send(
            java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("$baseUrl/oauth2/forward-auth"))
                .GET()
                .header("Accept", "application/json")
                .header("Cookie", "$authCookieName=${sessionTokenFor(member)}")
                .header("X-Forwarded-Host", host)
                .build(),
            java.net.http.HttpResponse.BodyHandlers.discarding(),
        )

        assertThat(response.statusCode()).isEqualTo(403)
    }

    @ParameterizedTest(name = "junk Authorization + valid cookie → 200 (Stalwart pattern, host={0})")
    @MethodSource("boardGatedHosts")
    fun junk_bearer_plus_cookie_still_authenticates(host: String) {
        // The Stalwart webadmin SPA sends its own opaque OAuth bearer in
        // `Authorization` alongside our BSH_AUTH cookie. JwtAuthFilter used to
        // short-circuit on the bearer and never read the cookie, leaving the
        // request anonymous → forward-auth 401 → SPA bounced back to /login.
        val board = TestHelper.registerActivateAndPromote("BOARD")
        val opaqueThirdPartyBearer = "QpT8jgss9DY3YS2YsIZrc4mLpgvEETjMEMDn+zR4gS+oUxl5"

        val response = java.net.http.HttpClient.newHttpClient().send(
            java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("$baseUrl/oauth2/forward-auth"))
                .GET()
                .header("Accept", "application/json")
                .header("Authorization", "Bearer $opaqueThirdPartyBearer")
                .header("Cookie", "$authCookieName=${sessionTokenFor(board)}")
                .header("X-Forwarded-Host", host)
                .build(),
            java.net.http.HttpResponse.BodyHandlers.discarding(),
        )

        assertThat(response.statusCode())
            .withFailMessage(
                "Expected forward-auth to fall back to the BSH_AUTH cookie when " +
                    "Authorization carries a non-Spring bearer; got ${response.statusCode()}",
            ).isEqualTo(200)
        assertThat(response.headers().firstValue("X-User-Groups").orElse("")).contains("BOARD")
    }

    @Test
    fun `unknown host falls back to ADMIN-required and rejects board`() {
        val board = TestHelper.registerActivateAndPromote("BOARD")

        val response = get(
            "/oauth2/forward-auth",
            sessionToken = sessionTokenFor(board),
            headers = mapOf("X-Forwarded-Host" to "rogue.example.com"),
        )

        assertThat(response.statusCode()).isEqualTo(302)
        assertThat(response.headers().firstValue("Location").orElse(""))
            .contains("/unauthorized?service=rogue.example.com")
    }
}
