package net.blueshell.api.auth.web

import net.blueshell.api.factory.auth.web.request.AuthRequestFactory
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
class AuthenticationControllerIT : UserTestSupport() {
    @Autowired
    private lateinit var authRequestFactory: AuthRequestFactory

    @Nested
    inner class Authenticate {
        @Test
        fun `auth cookie can authenticate protected endpoint without bearer header`() {
            val user = createUserWithRole(Role.MEMBER)

            val auth =
                mvc
                    .perform(
                        post("/auth")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(authRequestFactory.authenticatePayload(user.username, "Password123!")),
                    ).andExpect(status().isOk)
                    .andReturn()

            val authCookie = auth.response.cookies.firstOrNull { it.name == "BSH_AUTH" }
            assertThat(authCookie).isNotNull
            assertThat(authCookie!!.value).isNotBlank()

            mvc
                .perform(
                    get("/users/${user.id}")
                        .cookie(authCookie),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(user.id))
        }

        @Test
        fun `fails authentication with wrong password`() {
            val user = createUserWithRole(Role.MEMBER)

            mvc
                .perform(
                    post("/auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authRequestFactory.authenticatePayload(user.username, "WrongPassword123!")),
                ).andExpect(status().isUnauthorized)
        }

        @Test
        fun `rejects disabled users`() {
            val disabledUser = createUserWithRole(Role.MEMBER, enabled = false)

            mvc
                .perform(
                    post("/auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authRequestFactory.authenticatePayload(disabledUser.username, "Password123!")),
                ).andExpect(status().isUnauthorized)
        }

        @Test
        fun `returns bad request for blank username`() {
            val result =
                mvc
                    .perform(
                        post("/auth")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(authRequestFactory.authenticatePayload("", "Password123!")),
                    ).andExpect(status().isBadRequest)
                    .andReturn()

            assertThat(result.response.contentAsString).doesNotContain("\"rejectedValue\"")
        }
    }
}
