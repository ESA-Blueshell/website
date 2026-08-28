package net.blueshell.api.auth.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

// POST /signup is the only public door into registration, and POST /users is no
// longer one. Both halves of that are asserted here, because a regression on either
// side is silent: an open /users would let a signed-in member mint accounts, and a
// closed /signup would break registration entirely.
@SpringBootTest
class SignupControllerSecurityTest : UserTestSupport() {

    private fun registration(username: String) = """
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
        }
    """.trimIndent()

    @Nested
    inner class SignUp {

        @Test
        fun `allows an anonymous applicant to register`() {
            mvc.perform(
                post("/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registration("anon_applicant"))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.userId").isNumber)
                .andExpect(jsonPath("$.email").value("anon_applicant@example.com"))
                .andExpect(jsonPath("$.signupToken").isString)
                .andExpect(jsonPath("$.expiresAt").isString)
        }

        @Test
        fun `returns a token that is not the empty string`() {
            mvc.perform(
                post("/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registration("token_applicant"))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.signupToken").value(org.hamcrest.Matchers.containsString(".")))
        }

        @Test
        fun `rejects a registration missing the privacy consent`() {
            mvc.perform(
                post("/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registration("no_consent").replace("\"consentPrivacy\": true", "\"consentPrivacy\": false"))
            )
                .andExpect(status().is4xxClientError)
        }
    }

    @Nested
    inner class BoardOnlyUserCreation {

        @Test
        fun `denies an anonymous caller`() {
            mvc.perform(
                post("/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registration("anon_via_users"))
            )
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `denies a signed-in member`() {
            val member = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registration("member_via_users"))
                    .with(bearer(member))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `allows a board member`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                post("/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registration("board_via_users"))
                    .with(bearer(board))
            )
                .andExpect(status().isCreated)
        }
    }

    // The signup routes are only reachable from a browser if the preflight admits
    // the header that carries the session. Same-origin production hides a mistake
    // here; every cross-origin deployment breaks outright.
    @Nested
    inner class Preflight {

        @Test
        fun `admits the signup token header`() {
            mvc.perform(
                options("/signup/address")
                    .header("Origin", "http://localhost:3000")
                    .header("Access-Control-Request-Method", "POST")
                    .header("Access-Control-Request-Headers", SignupController.SIGNUP_TOKEN_HEADER)
            )
                .andExpect(status().isOk)
                .andExpect(
                    header().string(
                        "Access-Control-Allow-Headers",
                        org.hamcrest.Matchers.containsStringIgnoringCase(SignupController.SIGNUP_TOKEN_HEADER)
                    )
                )
        }
    }
}
