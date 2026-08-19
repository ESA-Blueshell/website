package net.blueshell.api.domain.auth.web

import net.blueshell.api.domain.auth.application.factory.RecoveryTokenFactory
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.enums.TokenPurpose
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration

// One case per row of the scope table in ADR-024. These are the tests that stop the
// signup token quietly widening into general authority over its account: every
// failure here is a privilege escalation, not a bug in a form.
@SpringBootTest
class SignupScopeSecurityTest : UserTestSupport() {

    @Autowired
    private lateinit var tokenFactory: RecoveryTokenFactory

    private val address = """
        {"country":"NL","city":"Enschede","street":"Drienerlolaan","houseNumber":"5","zipCode":"7522NB"}
    """.trimIndent()

    private fun signupTokenFor(user: net.blueshell.api.domain.user.persistence.User) =
        tokenFactory.issue(user, TokenPurpose.SIGNUP_CONTINUATION, Duration.ofHours(2))

    @Nested
    inner class WithinScope {

        @Test
        fun `saves an address on the token's own account`() {
            val applicant = assignMemberProfile(createUserWithRole(Role.GUEST, enabled = false))

            mvc.perform(
                post("/signup/address")
                    .header(SignupController.SIGNUP_TOKEN_HEADER, signupTokenFor(applicant))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(address)
            )
                .andExpect(status().isNoContent)
        }

        @Test
        fun `submits the application on the token's own account`() {
            val applicant = assignMemberProfile(assignAddress(createUserWithRole(Role.GUEST, enabled = false)))

            mvc.perform(
                post("/signup/apply")
                    .header(SignupController.SIGNUP_TOKEN_HEADER, signupTokenFor(applicant))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"conditionsAccepted":true}""")
            )
                .andExpect(status().isOk)
        }
    }

    @Nested
    inner class OutsideScope {

        @Test
        fun `a token minted for activation is refused`() {
            val applicant = assignMemberProfile(createUserWithRole(Role.GUEST, enabled = false))
            val activation = tokenFactory.issue(applicant, TokenPurpose.USER_ACTIVATION, Duration.ofHours(1))

            mvc.perform(
                post("/signup/address")
                    .header(SignupController.SIGNUP_TOKEN_HEADER, activation)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(address)
            )
                .andExpect(status().is4xxClientError)
        }

        @Test
        fun `a token minted for a password reset is refused`() {
            val applicant = assignMemberProfile(createUserWithRole(Role.GUEST, enabled = false))
            val reset = tokenFactory.issue(applicant, TokenPurpose.PASSWORD_RESET, Duration.ofHours(1))

            mvc.perform(
                post("/signup/address")
                    .header(SignupController.SIGNUP_TOKEN_HEADER, reset)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(address)
            )
                .andExpect(status().is4xxClientError)
        }

        @Test
        fun `a malformed token is refused`() {
            mvc.perform(
                post("/signup/address")
                    .header(SignupController.SIGNUP_TOKEN_HEADER, "not-a-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(address)
            )
                .andExpect(status().is4xxClientError)
        }

        @Test
        fun `an unknown token is refused`() {
            mvc.perform(
                post("/signup/address")
                    .header(SignupController.SIGNUP_TOKEN_HEADER, "unknown.verifier")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(address)
            )
                .andExpect(status().is4xxClientError)
        }

        @Test
        fun `an expired token is refused`() {
            val applicant = assignMemberProfile(createUserWithRole(Role.GUEST, enabled = false))
            val expired = tokenFactory.issue(applicant, TokenPurpose.SIGNUP_CONTINUATION, Duration.ofSeconds(-1))

            mvc.perform(
                post("/signup/address")
                    .header(SignupController.SIGNUP_TOKEN_HEADER, expired)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(address)
            )
                .andExpect(status().is4xxClientError)
        }

        @Test
        fun `a missing token header is refused`() {
            mvc.perform(
                post("/signup/address")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(address)
            )
                .andExpect(status().is4xxClientError)
        }

        @Test
        fun `the token cannot set a password`() {
            val applicant = assignMemberProfile(createUserWithRole(Role.GUEST, enabled = false))
            val signupToken = signupTokenFor(applicant)

            // The password endpoint takes its own token type; presenting a signup
            // token must not be accepted anywhere near it.
            mvc.perform(
                post("/recovery/password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"token":"$signupToken","password":"Hijacked1!"}""")
            )
                .andExpect(status().is4xxClientError)
        }

        @Test
        fun `the token cannot create a membership through the signed-in route`() {
            val applicant = assignMemberProfile(assignAddress(createUserWithRole(Role.GUEST, enabled = false)))
            val signupToken = signupTokenFor(applicant)

            // No session, and a signup token is not a credential the security
            // context understands, so the authenticated route stays shut.
            mvc.perform(
                post("/memberships")
                    .header(SignupController.SIGNUP_TOKEN_HEADER, signupToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"conditionsAccepted":true}""")
            )
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `the token cannot read the account back`() {
            val applicant = assignMemberProfile(createUserWithRole(Role.GUEST, enabled = false))
            val signupToken = signupTokenFor(applicant)

            mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .get("/users/{id}", applicant.id)
                    .header(SignupController.SIGNUP_TOKEN_HEADER, signupToken)
            )
                .andExpect(status().isUnauthorized)
        }
    }
}
