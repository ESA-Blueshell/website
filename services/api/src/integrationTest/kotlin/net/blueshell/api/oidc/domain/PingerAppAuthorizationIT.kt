package net.blueshell.api.oidc.domain

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * `pinger-app` is the one client a plain member signs in. It needs the member role and no step-up,
 * while every other client stays admin-only with a fresh two-factor code (see
 * AuthorizationEndpointSecurityTest).
 */
@SpringBootTest
class PingerAppAuthorizationIT : UserTestSupport() {
    // A loopback redirect on an ephemeral port the client bound: the authorization server allows
    // any port for 127.0.0.1, so this need not be the port the client registered with.
    private fun authorizeRequest() =
        get(
            "/oauth2/authorize?response_type=code&client_id=pinger-app" +
                "&redirect_uri=http://127.0.0.1:55123/login/oauth2/code/pinger-app&scope=openid" +
                "&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256",
        )

    @Test
    fun `a member completes the flow without an admin role or a step-up`() {
        val member = createUserWithRole(Role.MEMBER, twoFactor = true)

        val response =
            mvc
                .perform(authorizeRequest().with(signedIn(member)))
                .andExpect(status().isFound)
                .andReturn()
                .response

        assertThat(response.redirectedUrl.orEmpty())
            .startsWith("http://127.0.0.1:55123/login/oauth2/code/pinger-app")
            .contains("code=")
    }

    @Test
    fun `a non-member is refused`() {
        val company = createUserWithRole(Role.COMPANY)

        mvc
            .perform(authorizeRequest().with(signedIn(company)))
            .andExpect(status().isForbidden)
            .andExpect { assertThat(it.response.errorMessage).isEqualTo("Member access required") }
    }
}
