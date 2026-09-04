package net.blueshell.api.auth.domain

import net.blueshell.api.auth.web.AuthProblemDetailsAdvice
import net.blueshell.api.auth.web.SignupController
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.enums.TokenPurpose
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.user.api.MemberProfileService
import net.blueshell.api.user.persistence.User
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant

// A signup that lost its tab has to be able to pick itself up. Without a way to read
// the account back, the form came up empty, registered again on the applicant's own
// name and told them it was taken.
@SpringBootTest
class SignupResumeIT : UserTestSupport() {

    @Autowired
    private lateinit var tokenFactory: RecoveryTokenFactory

    @Autowired
    private lateinit var memberProfiles: MemberProfileService

    private fun applicant(enabled: Boolean = false) =
        assignMemberProfile(createUserWithRole(Role.GUEST, enabled = enabled))

    private fun signupToken(user: User, ttl: Duration = Duration.ofHours(2)) =
        tokenFactory.issue(user, TokenPurpose.SIGNUP_CONTINUATION, ttl)

    private fun resume(token: String) = mvc.perform(
        get("/signup/session").header(SignupController.SIGNUP_TOKEN_HEADER, token)
    )

    @Test
    fun `answers the details typed at the first step`() {
        val user = applicant()

        resume(signupToken(user))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value(user.id!!.toInt()))
            .andExpect(jsonPath("$.username").value(user.username))
            .andExpect(jsonPath("$.email").value(user.email))
            .andExpect(jsonPath("$.firstName").value(user.firstName))
            .andExpect(jsonPath("$.lastName").value(user.lastName))
            .andExpect(jsonPath("$.memberProfile").exists())
    }

    @Test
    fun `answers the address already saved`() {
        val user = assignAddress(applicant())
        requireNotNull(user.address) { "the fixture attaches an address" }

        resume(signupToken(user))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.address.city").value(user.address!!.city))
            .andExpect(jsonPath("$.address.street").value(user.address!!.street))
            .andExpect(jsonPath("$.address.zipCode").value(user.address!!.zipCode))
    }

    @Test
    fun `says there is no address yet when none was saved`() {
        resume(signupToken(applicant()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.address").doesNotExist())
    }

    @Test
    fun `says the email address is still unconfirmed`() {
        resume(signupToken(applicant()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.emailConfirmed").value(false))
            .andExpect(jsonPath("$.conditionsAccepted").value(false))
    }

    @Test
    fun `says the email address is confirmed once the account is enabled`() {
        resume(signupToken(applicant(enabled = true)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.emailConfirmed").value(true))
    }

    @Test
    fun `says the conditions are accepted once the application is in`() {
        val user = applicant()
        memberProfiles.update(
            memberProfiles.findById(user.id!!).apply { conditionsAcceptedAt = Instant.now() }
        )

        resume(signupToken(user))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.conditionsAccepted").value(true))
    }

    @Test
    fun `never answers with the password`() {
        resume(signupToken(applicant()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.password").doesNotExist())
    }

    @Test
    fun `refuses a token that was never issued`() {
        resume("not-a-token").andExpect(status().is4xxClientError)
    }

    /**
     * The code every signup step is refused with, asserted over http.
     *
     * `signupContinuation.ts` reads exactly this string to stand a stale tab down, and it
     * is the only thing stopping one pressing on against a retired token. Per ADR-012 a
     * refusal property is a ProblemDetail extension and is deliberately not in the
     * generated schema, so nothing on the wire declares it — which makes this the test
     * that says it is there. A rename belongs in the same commit as the frontend's.
     */
    @Test
    fun `a refused token answers the code the frontend stands down on`() {
        resume("not-a-token")
            .andExpect(jsonPath("$.code").value(AuthProblemDetailsAdvice.RECOVERY_TOKEN_UNUSABLE_CODE))
            .andExpect(jsonPath("$.code").value("RecoveryTokenUnusable"))
    }

    @Test
    fun `an expired token answers that same code`() {
        resume(signupToken(applicant(), ttl = Duration.ofSeconds(-1)))
            .andExpect(jsonPath("$.code").value("RecoveryTokenUnusable"))
    }

    @Test
    fun `refuses an activation token, which opens a different door`() {
        val user = applicant()
        val activation = tokenFactory.issue(user, TokenPurpose.USER_ACTIVATION, Duration.ofHours(1))

        resume(activation).andExpect(status().is4xxClientError)
    }

    @Test
    fun `refuses a token that has expired`() {
        val user = applicant()

        resume(signupToken(user, ttl = Duration.ofSeconds(-1))).andExpect(status().is4xxClientError)
    }

    @Test
    fun `refuses a request with no token at all`() {
        mvc.perform(get("/signup/session")).andExpect(status().is4xxClientError)
    }
}
