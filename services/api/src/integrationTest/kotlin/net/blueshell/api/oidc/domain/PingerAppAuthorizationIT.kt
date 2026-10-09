package net.blueshell.api.oidc.domain

import com.jayway.jsonpath.JsonPath
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
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

    @Test
    fun `the app trades its code for a refresh token and rotates it on every refresh`() {
        val member = createUserWithRole(Role.MEMBER, twoFactor = true)
        val first = redeemCode(member)

        val second = refresh(first)
        val third = refresh(second)

        assertThat(listOf(first, second, third)).doesNotHaveDuplicates()
        refreshRefused(first)
    }

    @Test
    fun `the app keeps refreshing after its member signs out of the website`() {
        val member = createUserWithRole(Role.MEMBER, twoFactor = true)
        val token = redeemCode(member)

        signIns.of(requireNotNull(member.id)).forEach { signIns.end(it.id) }

        refresh(token)
    }

    @Test
    fun `signing out everywhere ends the app's refreshes`() {
        val member = createUserWithRole(Role.MEMBER, twoFactor = true)
        val token = redeemCode(member)

        signIns.endAll(requireNotNull(member.id))

        refreshRefused(token)
    }

    @Test
    fun `a refresh naming a client that holds no refresh grant is refused as a client error`() {
        mvc
            .perform(tokenRequest("grant_type" to "refresh_token", "client_id" to "headlamp", "refresh_token" to "bogus"))
            .andExpect(status().is4xxClientError)
            .andExpect(jsonPath("$.error").exists())
            .andExpect(jsonPath("$.access_token").doesNotExist())
    }

    private fun redeemCode(member: User): String {
        val redirect =
            mvc
                .perform(authorizeRequest().with(signedIn(member)))
                .andReturn()
                .response.redirectedUrl
                .orEmpty()
        val code = redirect.substringAfter("code=").substringBefore('&')
        val body =
            mvc
                .perform(
                    tokenRequest(
                        "grant_type" to "authorization_code",
                        "client_id" to "pinger-app",
                        "code" to code,
                        "redirect_uri" to "http://127.0.0.1:55123/login/oauth2/code/pinger-app",
                        "code_verifier" to CODE_VERIFIER,
                    ),
                ).andExpect(status().isOk)
                .andReturn()
                .response.contentAsString
        return JsonPath.read(body, "$.refresh_token")
    }

    private fun refresh(refreshToken: String): String {
        val body =
            mvc
                .perform(tokenRequest("grant_type" to "refresh_token", "client_id" to "pinger-app", "refresh_token" to refreshToken))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.access_token").isNotEmpty)
                .andReturn()
                .response.contentAsString
        return JsonPath.read(body, "$.refresh_token")
    }

    private fun refreshRefused(refreshToken: String) {
        mvc
            .perform(tokenRequest("grant_type" to "refresh_token", "client_id" to "pinger-app", "refresh_token" to refreshToken))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("invalid_grant"))
    }

    private fun tokenRequest(vararg params: Pair<String, String>) =
        post("/oauth2/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .apply { params.forEach { (name, value) -> param(name, value) } }

    private companion object {
        // RFC 7636 appendix B: the verifier behind the challenge [authorizeRequest] sends.
        const val CODE_VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
    }
}
