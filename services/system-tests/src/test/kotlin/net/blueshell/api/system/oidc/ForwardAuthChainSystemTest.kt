package net.blueshell.api.system.oidc

import net.blueshell.api.shared.enums.Role
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/**
 * Lifts the existing ForwardAuthControllerIT integration test to the
 * system-test layer: drives /oauth2/forward-auth over real HTTP against
 * the embedded server. Parametrized across every host the controller
 * gates today (vault, headlamp, listmonk, stalwart, traefik) so a future
 * host added to HOST_ROLE without a system-test entry fails this suite.
 *
 * Models personal-stack's ForwardAuthChainSystemTest pattern.
 */
@Tag("system")
class ForwardAuthChainSystemTest : OidcSystemTestBase() {

    companion object {
        private const val FRONTEND_BASE = "https://v2.esa-blueshell.nl"

        @JvmStatic
        fun adminGatedHosts(): Stream<Arguments> = Stream.of(
            Arguments.of("vault.esa-blueshell.nl", Role.ADMIN),
            Arguments.of("headlamp.esa-blueshell.nl", Role.ADMIN),
            Arguments.of("traefik.esa-blueshell.nl", Role.ADMIN),
        )

        @JvmStatic
        fun boardGatedHosts(): Stream<Arguments> = Stream.of(
            Arguments.of("listmonk.esa-blueshell.nl", Role.BOARD),
            Arguments.of("stalwart.esa-blueshell.nl", Role.BOARD),
        )

        @JvmStatic
        fun allGatedHosts(): Stream<Arguments> =
            Stream.concat(adminGatedHosts(), boardGatedHosts())
    }

    @ParameterizedTest(name = "anonymous → 302 to /login (host={0})")
    @MethodSource("allGatedHosts")
    fun anonymous_redirects_to_login(host: String, @Suppress("UNUSED_PARAMETER") required: Role) {
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
    fun member_blocked_with_unauthorized_redirect(host: String, @Suppress("UNUSED_PARAMETER") required: Role) {
        val member = userFactory.createUserWithRole(Role.MEMBER)

        val response = get(
            "/oauth2/forward-auth",
            bearer = bearerToken(member.username),
            headers = mapOf("X-Forwarded-Host" to host, "X-Forwarded-Uri" to "/"),
        )

        assertThat(response.statusCode()).isEqualTo(302)
        val location = response.headers().firstValue("Location").orElse("")
        assertThat(location).isEqualTo("$FRONTEND_BASE/unauthorized?service=$host")
    }

    @ParameterizedTest(name = "admin → 200 with X-User-Id (host={0})")
    @MethodSource("allGatedHosts")
    fun admin_passes_every_host(host: String, @Suppress("UNUSED_PARAMETER") required: Role) {
        val admin = userFactory.createUserWithRole(Role.ADMIN)

        val response = get(
            "/oauth2/forward-auth",
            bearer = bearerToken(admin.username),
            headers = mapOf("X-Forwarded-Host" to host),
        )

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.headers().firstValue("X-User-Id").orElse(""))
            .isEqualTo(admin.id!!.toString())
        assertThat(response.headers().firstValue("X-User-Groups").orElse(""))
            .contains("ADMIN")
    }

    @ParameterizedTest(name = "board → 200 (host={0}, board-gated)")
    @MethodSource("boardGatedHosts")
    fun board_passes_board_gated_hosts(host: String, @Suppress("UNUSED_PARAMETER") required: Role) {
        val board = userFactory.createUserWithRole(Role.BOARD)

        val response = get(
            "/oauth2/forward-auth",
            bearer = bearerToken(board.username),
            headers = mapOf("X-Forwarded-Host" to host),
        )

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.headers().firstValue("X-User-Groups").orElse("")).contains("BOARD")
    }

    @ParameterizedTest(name = "board → 302 /unauthorized (host={0}, admin-gated)")
    @MethodSource("adminGatedHosts")
    fun board_blocked_on_admin_gated_hosts(host: String, @Suppress("UNUSED_PARAMETER") required: Role) {
        val board = userFactory.createUserWithRole(Role.BOARD)

        val response = get(
            "/oauth2/forward-auth",
            bearer = bearerToken(board.username),
            headers = mapOf("X-Forwarded-Host" to host),
        )

        assertThat(response.statusCode()).isEqualTo(302)
        assertThat(response.headers().firstValue("Location").orElse(""))
            .isEqualTo("$FRONTEND_BASE/unauthorized?service=$host")
    }

    @Test
    fun `unknown host falls back to ADMIN-required and rejects board`() {
        val board = userFactory.createUserWithRole(Role.BOARD)

        val response = get(
            "/oauth2/forward-auth",
            bearer = bearerToken(board.username),
            headers = mapOf("X-Forwarded-Host" to "rogue.example.com"),
        )

        assertThat(response.statusCode()).isEqualTo(302)
        assertThat(response.headers().firstValue("Location").orElse(""))
            .contains("/unauthorized?service=rogue.example.com")
    }
}
