package net.blueshell.api.oidc.domain

import net.blueshell.api.shared.enums.Role
import org.assertj.core.api.Assertions.assertThat
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Every client registered with this authorization server is an admin tool, so starting an
 * authorization flow is admin-only.
 *
 * Requests are built as query strings: the authorization server's own validation does not see
 * parameters added through MockMvc's builder, and rejects the request as malformed instead.
 */
@SpringBootTest
class AuthorizationEndpointSecurityTest : UserTestSupport() {

    // headlamp requires PKCE, so a request without a code challenge never reaches the gate.
    private fun authorizeRequest(clientId: String) =
        get(
            "/oauth2/authorize?response_type=code&client_id={c}" +
                "&redirect_uri=https://headlamp.esa-blueshell.nl/oidc-callback&scope=openid" +
                "&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256",
            clientId,
        )

    @Test
    fun `an unauthenticated request is sent to log in rather than served`() {
        mvc.perform(authorizeRequest("headlamp"))
            .andExpect(status().isFound)
            .andExpect(redirectedUrlPattern("/login?redirect=*"))
    }

    @Test
    fun `a member without the admin role is refused`() {
        val member = createUserWithRole(Role.MEMBER)

        mvc.perform(authorizeRequest("headlamp").with(bearer(member)))
            .andExpect(status().isForbidden)
            .andExpect { assertThat(it.response.errorMessage).isEqualTo(ADMIN_REFUSAL) }
    }

    @Test
    fun `a board member without the admin role is refused`() {
        val board = createUserWithRole(Role.BOARD)

        mvc.perform(authorizeRequest("headlamp").with(bearer(board)))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `an unregistered client is refused rather than waved through`() {
        val member = createUserWithRole(Role.MEMBER)

        mvc.perform(authorizeRequest("not-a-registered-client").with(bearer(member)))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `a request with no client_id at all is refused`() {
        val member = createUserWithRole(Role.MEMBER)

        mvc.perform(get("/oauth2/authorize?response_type=code&scope=openid").with(bearer(member)))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `an admin is let past the gate`() {
        val admin = createUserWithRole(Role.ADMIN)

        // The assertion is about this gate and nothing further: the authorization server
        // refuses the request afterwards for its own reasons, which the gate does not decide.
        val response = mvc.perform(authorizeRequest("headlamp").with(bearer(admin))).andReturn().response

        assertThat(response.errorMessage).isNotEqualTo(ADMIN_REFUSAL)
        assertThat(response.status).isNotEqualTo(HttpStatus.FORBIDDEN.value())
    }

    private companion object {
        /** What the gate itself says when it refuses, as distinct from any later refusal. */
        const val ADMIN_REFUSAL = "Admin access required"
    }
}
