package net.blueshell.api.domain.auth.web

import net.blueshell.api.factory.auth.web.request.AuthRequestFactory
import net.blueshell.api.infrastructure.security.JwtTokenUtil
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.hamcrest.Matchers.containsString
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
class AuthenticationControllerIT : UserTestSupport() {

    @Autowired
    private lateinit var jwtTokenUtil: JwtTokenUtil

    @Autowired
    private lateinit var authRequestFactory: AuthRequestFactory

    @Nested
    inner class Authenticate {
        @Test
        fun `authenticates valid credentials and returns jwt payload`() {
            val user = createUserWithRole(Role.MEMBER)

            val result = mvc.perform(
                post("/auth")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(authRequestFactory.authenticatePayload(user.username, "Password123!"))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.token").isNotEmpty)
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("BSH_AUTH=")))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("HttpOnly")))
                .andExpect(jsonPath("$.userId").value(user.id))
                .andExpect(jsonPath("$.username").value(user.username))
                .andReturn()

            val body = mapper.readTree(result.response.contentAsByteArray)
            val token = body.path("token").asText()
            val expiration = body.path("expiration").asLong()
            val validation = jwtTokenUtil.parseAndValidate(token)

            assertThat(jwtTokenUtil.isTokenValid(token)).isTrue()
            assertThat(jwtTokenUtil.getUsernameFromToken(token)).isEqualTo(user.username)
            assertThat(validation.jti).isNotBlank()
            assertThat(expiration).isGreaterThan(System.currentTimeMillis())
        }

        @Test
        fun `auth cookie can authenticate protected endpoint without bearer header`() {
            val user = createUserWithRole(Role.MEMBER)

            val auth = mvc.perform(
                post("/auth")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(authRequestFactory.authenticatePayload(user.username, "Password123!"))
            )
                .andExpect(status().isOk)
                .andReturn()

            val authCookie = auth.response.cookies.firstOrNull { it.name == "BSH_AUTH" }
            assertThat(authCookie).isNotNull
            assertThat(authCookie!!.value).isNotBlank()

            mvc.perform(
                get("/users/${user.id}")
                    .cookie(authCookie)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(user.id))
        }

        @Test
        fun `logout revokes token jti so bearer token can no longer authenticate`() {
            val user = createUserWithRole(Role.MEMBER)

            val auth = mvc.perform(
                post("/auth")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(authRequestFactory.authenticatePayload(user.username, "Password123!"))
            )
                .andExpect(status().isOk)
                .andReturn()

            val token = mapper.readTree(auth.response.contentAsByteArray).path("token").asText()
            assertThat(token).isNotBlank()

            mvc.perform(
                get("/users/${user.id}")
                    .header("Authorization", "Bearer $token")
            )
                .andExpect(status().isOk)

            mvc.perform(
                post("/auth/logout")
                    .header("Authorization", "Bearer $token")
            )
                .andExpect(status().isNoContent)
                .andExpect(header().string("Set-Cookie", containsString("BSH_AUTH=")))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")))

            mvc.perform(
                get("/users/${user.id}")
                    .header("Authorization", "Bearer $token")
            )
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `fails authentication with wrong password`() {
            val user = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/auth")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(authRequestFactory.authenticatePayload(user.username, "WrongPassword123!"))
            )
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `rejects disabled users`() {
            val disabledUser = createUserWithRole(Role.MEMBER, enabled = false)

            mvc.perform(
                post("/auth")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(authRequestFactory.authenticatePayload(disabledUser.username, "Password123!"))
            )
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `returns bad request for blank username`() {
            val result = mvc.perform(
                post("/auth")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(authRequestFactory.authenticatePayload("", "Password123!"))
            )
                .andExpect(status().isBadRequest)
                .andReturn()

            assertThat(result.response.contentAsString).doesNotContain("\"rejectedValue\"")
        }
    }
}
