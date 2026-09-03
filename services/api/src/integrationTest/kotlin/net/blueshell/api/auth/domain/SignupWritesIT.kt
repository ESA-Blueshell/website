package net.blueshell.api.auth.domain

import net.blueshell.api.user.api.MemberProfileService
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.MemberRepository
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.enums.TokenPurpose
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import net.blueshell.api.auth.web.SignupController

@SpringBootTest
class SignupWritesIT : UserTestSupport() {

    @Autowired
    private lateinit var tokenFactory: RecoveryTokenFactory

    @Autowired
    private lateinit var users: UserService

    @Autowired
    private lateinit var memberProfiles: MemberProfileService

    @Autowired
    private lateinit var memberships: MemberRepository

    private val addressPayload = """
        {"country":"NL","city":"Enschede","street":"Drienerlolaan","houseNumber":"5","zipCode":"7522NB"}
    """.trimIndent()

    private fun applicant(enabled: Boolean = false) =
        assignMemberProfile(createUserWithRole(Role.GUEST, enabled = enabled))

    private fun tokenFor(user: net.blueshell.api.user.persistence.User) =
        tokenFactory.issue(user, TokenPurpose.SIGNUP_CONTINUATION, Duration.ofHours(2))

    private fun saveAddress(token: String) = mvc.perform(
        post("/signup/address")
            .header(SignupController.SIGNUP_TOKEN_HEADER, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(addressPayload)
    )

    private fun apply(token: String) = mvc.perform(
        post("/signup/apply")
            .header(SignupController.SIGNUP_TOKEN_HEADER, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"conditionsAccepted":true}""")
    )

    @Test
    fun `saves the address against the account the token speaks for`() {
        val user = applicant()

        saveAddress(tokenFor(user)).andExpect(status().isNoContent)

        assertThat(refreshUser(user).addressId).isNotNull()
    }

    @Test
    fun `saving twice replaces the address rather than adding one`() {
        val user = applicant()
        val token = tokenFor(user)

        saveAddress(token).andExpect(status().isNoContent)
        val first = refreshUser(user).addressId

        mvc.perform(
            post("/signup/address")
                .header(SignupController.SIGNUP_TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(addressPayload.replace("\"houseNumber\":\"5\"", "\"houseNumber\":\"7\""))
        ).andExpect(status().isNoContent)

        assertThat(refreshUser(user).address!!.houseNumber)
            .describedAs("going back a step must correct the address, not accumulate")
            .isEqualTo("7")
        assertThat(first).isNotNull()
    }

    @Test
    fun `applying before the email is confirmed records the acceptance without joining`() {
        val user = applicant(enabled = false)
        val token = tokenFor(user)
        saveAddress(token).andExpect(status().isNoContent)

        apply(token)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.emailConfirmed").value(false))
            .andExpect(jsonPath("$.membershipStarted").value(false))

        assertThat(memberProfiles.findById(user.id!!).conditionsAcceptedAt).isNotNull()
        assertThat(memberships.findByUser_Id(user.id!!)).isEmpty()
    }

    @Test
    fun `applying after the email is confirmed starts the membership`() {
        val user = applicant(enabled = true)
        val token = tokenFor(user)
        saveAddress(token).andExpect(status().isNoContent)

        apply(token)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.membershipStarted").value(true))

        assertThat(memberships.findByUser_Id(user.id!!)).hasSize(1)
    }

    @Test
    fun `the token is refused once the membership has started`() {
        val user = applicant(enabled = true)
        val token = tokenFor(user)
        saveAddress(token).andExpect(status().isNoContent)
        apply(token).andExpect(status().isOk)

        // completeIfReady retired it, so the session is spent.
        saveAddress(token).andExpect(status().is4xxClientError)
    }

    @Test
    fun `applying twice does not join twice`() {
        val user = applicant(enabled = true)
        val token = tokenFor(user)
        saveAddress(token).andExpect(status().isNoContent)
        apply(token).andExpect(status().isOk)

        apply(token).andExpect(status().is4xxClientError)

        assertThat(memberships.findByUser_Id(user.id!!)).hasSize(1)
    }

    @Test
    fun `an account that never applied for membership cannot submit an application`() {
        val plainAccount = createUserWithRole(Role.GUEST, enabled = true)

        apply(tokenFor(plainAccount)).andExpect(status().is4xxClientError)
    }

    // Refused outside the dispatch, this answered 403 with no body, and the applicant
    // was told they lacked authority for a step that was only missing a profile.
    @Test
    fun `says why an application without a profile is refused`() {
        val plainAccount = createUserWithRole(Role.GUEST, enabled = true)

        apply(tokenFor(plainAccount))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.detail").value("This signup did not apply for membership"))
    }

    @Test
    fun `the address cannot be saved against somebody else`() {
        val applicant = applicant()
        val bystander = applicant()

        saveAddress(tokenFor(applicant)).andExpect(status().isNoContent)

        assertThat(refreshUser(bystander).addressId)
            .describedAs("a token must only ever write to its own account")
            .isNull()
        assertThat(users.findById(applicant.id!!).addressId).isNotNull()
    }
}
