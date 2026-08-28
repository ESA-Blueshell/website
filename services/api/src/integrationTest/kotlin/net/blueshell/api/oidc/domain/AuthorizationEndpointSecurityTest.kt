package net.blueshell.api.oidc.domain

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Every client registered with this authorization server is an admin tool, so starting an
 * authorization flow is admin-only.
 *
 * The gate used to read `client_id` off the request and skip the admin check when the value
 * was not one it recognised, which let the request decide whether it was checked at all. The
 * unknown-client case below is what fails if that branching comes back.
 */
@SpringBootTest
class AuthorizationEndpointSecurityTest : UserTestSupport() {

    // headlamp is the PKCE client, so a request without a code challenge is rejected as
    // malformed before authentication is considered. The challenge is a fixed dummy: nothing
    // here exchanges the code, so only its presence matters.
    // headlamp is the PKCE client, so a request without a code challenge is rejected as
    // malformed before authentication is considered. The challenge is a fixed dummy: nothing
    // here exchanges the code, so only its presence matters.
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

        // The old gate skipped the admin check for any client_id it did not recognise, so this
        // request reached the authorization server instead of being refused here.
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

        // Past the gate the authorization server takes over, so anything other than the 403
        // this class is about counts as let through.
        val status = mvc.perform(authorizeRequest("headlamp").with(bearer(admin)))
            .andReturn().response.status

        assert(status != 403) { "an admin was refused by the admin gate (status $status)" }
    }
}
