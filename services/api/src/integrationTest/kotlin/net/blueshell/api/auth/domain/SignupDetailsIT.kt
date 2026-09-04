package net.blueshell.api.auth.domain

import net.blueshell.api.user.api.MemberProfileService
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.enums.TokenPurpose
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import net.blueshell.api.auth.web.SignupController

// Going back a step has to be a real edit, not a form that looks editable and
// discards what was typed. Each case here is a field the applicant can still fix
// while the address is unconfirmed.
@SpringBootTest
class SignupDetailsIT : UserTestSupport() {

    @Autowired
    private lateinit var tokenFactory: RecoveryTokenFactory

    @Autowired
    private lateinit var memberProfiles: MemberProfileService

    private fun applicant(enabled: Boolean = false) =
        assignMemberProfile(createUserWithRole(Role.GUEST, enabled = enabled))

    private fun signupToken(user: net.blueshell.api.user.persistence.User) =
        tokenFactory.issue(user, TokenPurpose.SIGNUP_CONTINUATION, Duration.ofHours(2))

    private fun details(
        username: String,
        firstName: String = "Corrected",
        discord: String = "corrected#0001",
        phoneNumber: String = "0612345678",
    ) = """
        {
          "username": "$username",
          "initials": "CU",
          "firstName": "$firstName",
          "lastName": "Applicant",
          "discord": "$discord",
          "phoneNumber": "$phoneNumber",
          "newsletter": true,
          "photoConsent": false
        }
    """.trimIndent()

    private fun update(token: String, body: String) = mvc.perform(
        patch("/signup/details")
            .header(SignupController.SIGNUP_TOKEN_HEADER, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)
    )

    @Test
    fun `corrects the name typed at the first step`() {
        val user = applicant()

        update(signupToken(user), details(username = user.username, firstName = "Fixed"))
            .andExpect(status().isNoContent)

        val reloaded = refreshUser(user)
        assertThat(reloaded.firstName).isEqualTo("Fixed")
        assertThat(reloaded.newsletter).isTrue()
    }

    @Test
    fun `changes the username while it is still unconfirmed`() {
        val user = applicant()
        val wanted = "renamed${System.nanoTime()}"

        update(signupToken(user), details(username = wanted)).andExpect(status().isNoContent)

        assertThat(refreshUser(user).username).isEqualTo(wanted)
    }

    @Test
    fun `keeps the email address out of reach`() {
        val user = applicant()
        val original = user.email

        // The body carries no email field at all; a correction goes through
        // PATCH /signup/email so the confirmation link is reissued with it.
        update(signupToken(user), details(username = user.username)).andExpect(status().isNoContent)

        assertThat(refreshUser(user).email).isEqualTo(original)
    }

    @Test
    fun `refuses a username somebody else holds`() {
        val user = applicant()
        val other = createUserWithRole(Role.MEMBER)

        update(signupToken(user), details(username = other.username)).andExpect(status().isConflict)

        assertThat(refreshUser(user).username).isNotEqualTo(other.username)
    }

    @Test
    fun `refuses a discord name somebody else holds`() {
        val user = applicant()
        val other = createUserWithRole(Role.MEMBER)

        update(signupToken(user), details(username = user.username, discord = other.discord!!))
            .andExpect(status().isConflict)
    }

    @Test
    fun `accepts the applicant's own details unchanged`() {
        val user = applicant()

        // Re-submitting the form without edits must not read as a conflict with
        // the applicant's own account.
        update(
            signupToken(user),
            details(username = user.username, discord = user.discord!!, phoneNumber = user.phoneNumber!!)
        ).andExpect(status().isNoContent)
    }

    @Test
    fun `updates the member profile alongside the account`() {
        val user = applicant()
        val body = """
            {
              "username": "${user.username}",
              "initials": "CU",
              "firstName": "Corrected",
              "lastName": "Applicant",
              "discord": "${user.discord}",
              "phoneNumber": "${user.phoneNumber}",
              "newsletter": false,
              "photoConsent": true,
              "memberProfile": {
                "dateOfBirth": "1998-03-04",
                "nationality": "Dutch",
                "studentNumber": "s7654321",
                "gender": "X",
                "bhv": true,
                "ehbo": true
              }
            }
        """.trimIndent()

        update(signupToken(user), body).andExpect(status().isNoContent)

        val profile = memberProfiles.findById(user.id!!)
        assertThat(profile.studentNumber).isEqualTo("s7654321")
        assertThat(profile.bhv).isTrue()
    }

    @Test
    fun `refuses once the address is confirmed`() {
        val user = applicant(enabled = true)

        // A confirmed account has a session to change its details under, and the
        // signup token must not outlive that boundary. The reason travels: without it
        // the applicant was told they lacked authority for their own account.
        update(signupToken(user), details(username = user.username, firstName = "TooLate"))
            .andExpect(status().is4xxClientError)
            .andExpect(jsonPath("$.detail").value("A confirmed account changes its details under a session"))

        assertThat(refreshUser(user).firstName).isNotEqualTo("TooLate")
    }

    @Test
    fun `names the field a taken username collides with`() {
        val user = applicant()
        val other = createUserWithRole(Role.MEMBER)

        // A conflict carries no `errors` array, so the sentence is all the form has to
        // show. It told the applicant to reload the page, which lost the signup.
        update(signupToken(user), details(username = other.username))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.detail").value("That username is already in use"))
    }

    @Test
    fun `refuses a body with nothing in the required fields`() {
        val user = applicant()

        update(signupToken(user), """{"username":"","initials":"","firstName":"","lastName":""}""")
            .andExpect(status().isBadRequest)
    }
}
