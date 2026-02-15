package net.blueshell.api.domain.auth.web

import net.blueshell.api.infrastructure.security.JwtTokenUtil
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
class AuthenticationControllerIT : UserTestSupport() {

    @Autowired
    private lateinit var jwtTokenUtil: JwtTokenUtil

    @Test
    fun `authenticates valid credentials and returns jwt payload`() {
        val user = createUserWithRole(Role.MEMBER)

        val result = mvc.perform(
            post("/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"${user.username}","password":"Password123!"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").isNotEmpty)
            .andExpect(jsonPath("$.userId").value(user.id))
            .andExpect(jsonPath("$.username").value(user.username))
            .andReturn()

        val body = mapper.readTree(result.response.contentAsByteArray)
        val token = body.path("token").asText()
        val expiration = body.path("expiration").asLong()

        assertThat(jwtTokenUtil.isTokenValid(token)).isTrue()
        assertThat(jwtTokenUtil.getUsernameFromToken(token)).isEqualTo(user.username)
        assertThat(expiration).isGreaterThan(System.currentTimeMillis())
    }

    @Test
    fun `fails authentication with wrong password`() {
        val user = createUserWithRole(Role.MEMBER)

        mvc.perform(
            post("/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"${user.username}","password":"WrongPassword123!"}""")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `rejects disabled users`() {
        val disabledUser = createUserWithRole(Role.MEMBER, enabled = false)

        mvc.perform(
            post("/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"${disabledUser.username}","password":"Password123!"}""")
        )
            .andExpect(status().isUnauthorized)
    }
}
