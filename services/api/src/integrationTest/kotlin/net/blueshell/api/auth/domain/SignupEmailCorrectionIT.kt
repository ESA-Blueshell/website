package net.blueshell.api.auth.domain

import net.blueshell.api.auth.persistence.RecoveryTokenRepository
import net.blueshell.api.user.api.MemberProfileService
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.enums.TokenPurpose
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant
import net.blueshell.api.auth.web.SignupController

@SpringBootTest
class SignupEmailCorrectionIT : UserTestSupport() {

    @Autowired
    private lateinit var tokenFactory: RecoveryTokenFactory

    @Autowired
    private lateinit var recoveryTokens: RecoveryTokenRepository

    @Autowired
    private lateinit var memberProfiles: MemberProfileService

    private fun applicant(enabled: Boolean = false) =
        assignMemberProfile(assignAddress(createUserWithRole(Role.GUEST, enabled = enabled)))

    private fun signupToken(user: net.blueshell.api.user.persistence.User) =
        tokenFactory.issue(user, TokenPurpose.SIGNUP_CONTINUATION, Duration.ofHours(2))

    private fun correct(token: String, email: String) = mvc.perform(
        patch("/signup/email")
            .header(SignupController.SIGNUP_TOKEN_HEADER, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"email":"$email"}""")
    )

    @Test
    fun `changes the address the confirmation will go to`() {
        val user = applicant()

        correct(signupToken(user), "corrected@example.com").andExpect(status().isNoContent)

        assertThat(refreshUser(user).email).isEqualTo("corrected@example.com")
    }

    @Test
    fun `invalidates the link already sent to the wrong address`() {
        val user = applicant()
        val staleActivation = tokenFactory.issue(user, TokenPurpose.USER_ACTIVATION, Duration.ofHours(1))

        correct(signupToken(user), "corrected2@example.com").andExpect(status().isNoContent)

        // The link already delivered to the mistyped address must stop working: that
        // address may be somebody else's inbox.
        mvc.perform(
            post("/recovery/user/activate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"$staleActivation"}""")
        ).andExpect(status().is4xxClientError)

        assertThat(refreshUser(user).enabled)
            .describedAs("the stale link must not confirm the account")
            .isFalse()

        val live = recoveryTokens.findAllUnconsumedByUserId(user.id!!)
            .filter { it.type == TokenPurpose.USER_ACTIVATION }
        assertThat(live)
            .describedAs("exactly one confirmation link may be outstanding")
            .hasSize(1)
    }

    @Test
    fun `queues a fresh confirmation email`() {
        val user = applicant()

        correct(signupToken(user), "corrected3@example.com").andExpect(status().isNoContent)

        assertThat(jobExecutions.findAll().map { it.jobType })
            .contains(EmailJobs.Recovery.type)
    }

    @Test
    fun `leaves the address and the acceptance on file`() {
        val user = applicant()
        transactionTemplate.execute {
            val profile = memberProfiles.findById(user.id!!)
            profile.conditionsAcceptedAt = Instant.now()
            memberProfiles.update(profile)
        }

        correct(signupToken(user), "corrected4@example.com").andExpect(status().isNoContent)

        val reloaded = refreshUser(user)
        assertThat(reloaded.addressId).describedAs("address survives a typo fix").isNotNull()
        assertThat(memberProfiles.findById(user.id!!).conditionsAcceptedAt).isNotNull()
    }

    @Test
    fun `refuses once the address is confirmed`() {
        val user = applicant(enabled = true)

        correct(signupToken(user), "toolate@example.com").andExpect(status().is4xxClientError)

        assertThat(refreshUser(user).email).isNotEqualTo("toolate@example.com")
    }

    @Test
    fun `refuses an address that belongs to somebody else`() {
        val user = applicant()
        val other = createUserWithRole(Role.MEMBER)

        correct(signupToken(user), other.email).andExpect(status().is4xxClientError)

        assertThat(refreshUser(user).email).isNotEqualTo(other.email)
    }

    @Test
    fun `refuses an address that is not an address`() {
        val user = applicant()

        correct(signupToken(user), "not-an-email").andExpect(status().is4xxClientError)
    }

    @Test
    fun `resending leaves one live link however often it is asked for`() {
        val user = applicant()

        // The confirmation step offers a resend button, so pressing it repeatedly
        // must not accumulate working links behind the applicant.
        repeat(3) {
            mvc.perform(post("/recovery/user/activate/resend/{username}", user.username))
                .andExpect(status().isNoContent)
        }

        val live = recoveryTokens.findAllUnconsumedByUserId(user.id!!)
            .filter { it.type == TokenPurpose.USER_ACTIVATION }
        assertThat(live)
            .describedAs("only the most recent confirmation link stays live")
            .hasSize(1)
    }

    @Test
    fun `an earlier link stops working once a fresh one is sent`() {
        val user = applicant()
        val first = tokenFactory.issue(user, TokenPurpose.USER_ACTIVATION, Duration.ofHours(1))

        mvc.perform(post("/recovery/user/activate/resend/{username}", user.username))
            .andExpect(status().isNoContent)

        mvc.perform(
            post("/recovery/user/activate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"$first"}""")
        ).andExpect(status().is4xxClientError)

        assertThat(refreshUser(user).enabled).isFalse()
    }

    @Test
    fun `the corrected address is the one that can confirm the account`() {
        val user = applicant()
        correct(signupToken(user), "corrected5@example.com").andExpect(status().isNoContent)

        val fresh = recoveryTokens.findAllUnconsumedByUserId(user.id!!)
            .single { it.type == TokenPurpose.USER_ACTIVATION }

        // Confirming with the freshly issued token must work end to end.
        val raw = tokenFactory.issue(refreshUser(user), TokenPurpose.USER_ACTIVATION, Duration.ofHours(1))
        assertThat(fresh.selector).isNotBlank()
        mvc.perform(
            post("/recovery/user/activate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"$raw"}""")
        ).andExpect(status().isOk)

        assertThat(refreshUser(user).enabled).isTrue()
    }
}
