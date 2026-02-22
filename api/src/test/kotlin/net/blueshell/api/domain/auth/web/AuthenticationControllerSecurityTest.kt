package net.blueshell.api.domain.auth.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Security tests for AuthenticationController.
 *
 * Verifies authentication handling:
 * - Valid credentials return JWT token
 * - Invalid credentials return 401
 * - Disabled users cannot authenticate
 * - Unauthenticated users can attempt authentication
 */
@SpringBootTest
class AuthenticationControllerSecurityTest : UserTestSupport() {

    @Nested
    inner class Authenticate {

        @Test
        fun `allows anyone to attempt authentication`() {
            val user = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/auth")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"username":"${user.username}","password":"Password123!"}""")
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `returns 401 for invalid credentials`() {
            mvc.perform(
                post("/auth")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"username":"nonexistent","password":"WrongPassword123!"}""")
            )
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `returns 401 for disabled user`() {
            val disabledUser = createUserWithRole(Role.MEMBER, enabled = false)

            mvc.perform(
                post("/auth")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"username":"${disabledUser.username}","password":"Password123!"}""")
            )
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `returns 401 for wrong password`() {
            val user = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/auth")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"username":"${user.username}","password":"WrongPassword123!"}""")
            )
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `allows BOARD to authenticate`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                post("/auth")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"username":"${board.username}","password":"Password123!"}""")
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows GUEST to authenticate`() {
            val guest = createUserWithRole(Role.GUEST)

            mvc.perform(
                post("/auth")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"username":"${guest.username}","password":"Password123!"}""")
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows ADMIN to authenticate`() {
            val admin = createUserWithRole(Role.ADMIN)

            mvc.perform(
                post("/auth")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"username":"${admin.username}","password":"Password123!"}""")
            )
                .andExpect(status().isOk)
        }
    }

    @Nested
    inner class PublicAccess {

        @Test
        fun `authentication endpoint is public`() {
            mvc.perform(
                post("/auth")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"username":"anyuser","password":"anypassword"}""")
            )
                .andExpect(status().isUnauthorized) // Wrong credentials, but endpoint is accessible
        }

        @Test
        fun `authenticated user can also authenticate`() {
            val user = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/auth")
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"username":"${user.username}","password":"Password123!"}""")
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `logout endpoint is public and clears auth cookie`() {
            mvc.perform(
                post("/auth/logout")
            )
                .andExpect(status().isNoContent)
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("BSH_AUTH=")))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")))
        }
    }
}
