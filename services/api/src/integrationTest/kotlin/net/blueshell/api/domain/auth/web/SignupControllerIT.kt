package net.blueshell.api.domain.auth.web

import net.blueshell.api.domain.auth.persistence.repository.RecoveryTokenRepository
import net.blueshell.api.shared.enums.TokenPurpose
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
class SignupControllerIT : UserTestSupport() {

    @Autowired
    private lateinit var recoveryTokens: RecoveryTokenRepository

    private fun registration(username: String, withMemberProfile: Boolean) = """
        {
          "username": "$username",
          "initials": "TU",
          "firstName": "Test",
          "lastName": "User",
          "email": "$username@example.com",
          "discord": "$username#0001",
          "phoneNumber": "0612345678",
          "newsletter": false,
          "consentPrivacy": true,
          "photoConsent": false,
          "password": "Passw0rd!"
          ${if (withMemberProfile) ""","memberProfile":{"dateOfBirth":"2000-01-01","nationality":"NL","bhv":false,"ehbo":false}""" else ""}
        }
    """.trimIndent()

    private fun signUp(username: String, withMemberProfile: Boolean = true) =
        mvc.perform(
            post("/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registration(username, withMemberProfile))
        )

    @Test
    fun `creates an account that cannot be used yet`() {
        signUp("it_applicant").andExpect(status().isCreated)

        val user = userRepository.findByUsername("it_applicant").orElseThrow()
        assertThat(user.enabled)
            .describedAs("a fresh signup must not be able to sign in")
            .isFalse()
    }

    @Test
    fun `creates the member profile the application needs`() {
        signUp("it_profile").andExpect(status().isCreated)

        val user = userRepository.findByUsername("it_profile").orElseThrow()
        assertThat(refreshUser(user).memberProfile).isNotNull()
    }

    @Test
    fun `issues one live signup token and one activation token`() {
        signUp("it_tokens").andExpect(status().isCreated)
        val userId = userRepository.findByUsername("it_tokens").orElseThrow().id!!

        val live = recoveryTokens.findAllUnconsumedByUserId(userId)
        assertThat(live.filter { it.type == TokenPurpose.SIGNUP_CONTINUATION })
            .describedAs("exactly one signup session")
            .hasSize(1)
        assertThat(live.filter { it.type == TokenPurpose.USER_ACTIVATION })
            .describedAs("exactly one confirmation link")
            .hasSize(1)
    }

    @Test
    fun `the signup token is never the one that was emailed`() {
        val response = signUp("it_distinct").andExpect(status().isCreated).andReturn()
        val returned = com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(response.response.contentAsString)["signupToken"].asText()
        val userId = userRepository.findByUsername("it_distinct").orElseThrow().id!!

        val activationSelector = recoveryTokens.findAllUnconsumedByUserId(userId)
            .single { it.type == TokenPurpose.USER_ACTIVATION }
            .selector

        assertThat(returned.substringBefore('.'))
            .describedAs("the token handed to the browser must not be the emailed one")
            .isNotEqualTo(activationSelector)
    }

    @Test
    fun `refuses a duplicate username`() {
        signUp("it_duplicate").andExpect(status().isCreated)

        signUp("it_duplicate").andExpect(status().is4xxClientError)
    }
}
