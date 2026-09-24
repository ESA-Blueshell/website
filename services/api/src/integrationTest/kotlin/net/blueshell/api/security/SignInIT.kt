package net.blueshell.api.security

import jakarta.servlet.http.Cookie
import net.blueshell.api.factory.auth.web.request.AuthRequestFactory
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.time.SettableClock
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration

@SpringBootTest
class SignInIT : UserTestSupport() {
    @Autowired
    private lateinit var authRequestFactory: AuthRequestFactory

    @Autowired
    private lateinit var clock: SettableClock

    private fun signIn(
        user: User,
        userAgent: String = FIREFOX,
    ): Cookie {
        val result =
            mvc
                .perform(
                    post("/auth")
                        .header("User-Agent", userAgent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authRequestFactory.authenticatePayload(user.username, "Password123!")),
                ).andExpect(status().isOk)
                .andReturn()
        return result.response.cookies.first { it.name == "BSH_AUTH" }
    }

    private fun read(
        user: User,
        cookie: Cookie,
        userAgent: String = FIREFOX,
    ) = mvc.perform(get("/users/${user.id}").header("User-Agent", userAgent).cookie(cookie))

    private fun rotatedCookie(
        user: User,
        cookie: Cookie,
    ): Cookie =
        read(user, cookie)
            .andExpect(status().isOk)
            .andReturn()
            .response.cookies
            .first { it.name == "BSH_AUTH" }

    @Test
    fun `the answer names who signed in and never the token`() {
        val user = createUserWithRole(Role.MEMBER)

        mvc
            .perform(
                post("/auth")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(authRequestFactory.authenticatePayload(user.username, "Password123!")),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value(user.id))
            .andExpect(jsonPath("$.token").doesNotExist())
            .andExpect(jsonPath("$.expiration").doesNotExist())
    }

    @Test
    fun `the cookie signs requests in, and the same token as a bearer header does not`() {
        val user = createUserWithRole(Role.MEMBER)
        val cookie = signIn(user)

        read(user, cookie).andExpect(status().isOk)
        mvc
            .perform(get("/users/${user.id}").header("Authorization", "Bearer ${cookie.value}"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `the session cookie does not sign a request in on its own`() {
        val user = createUserWithRole(Role.MEMBER)
        val warmed = read(user, signIn(user)).andExpect(status().isOk).andReturn()
        val session = warmed.request.getSession(false) as MockHttpSession? ?: MockHttpSession()

        mvc.perform(get("/users/${user.id}").session(session)).andExpect(status().isUnauthorized)
    }

    @Test
    fun `logout ends the sign-in on the server`() {
        val user = createUserWithRole(Role.MEMBER)
        val cookie = signIn(user)

        mvc.perform(post("/auth/logout").cookie(cookie)).andExpect(status().isNoContent)

        read(user, cookie).andExpect(status().isUnauthorized)
    }

    @Test
    fun `a cookie older than five minutes is rotated and the old one is refused after its grace`() {
        val user = createUserWithRole(Role.MEMBER)
        val first = signIn(user)

        clock.advance(Duration.ofMinutes(5))
        val rotated = rotatedCookie(user, first)
        assertThat(rotated.value).isNotEqualTo(first.value)

        clock.advance(Duration.ofSeconds(59))
        read(user, first).andExpect(status().isOk)

        clock.advance(Duration.ofSeconds(2))
        read(user, first).andExpect(status().isUnauthorized)
        read(user, rotated).andExpect(status().isUnauthorized)
    }

    @Test
    fun `ten requests across a rotation all succeed`() {
        val user = createUserWithRole(Role.MEMBER)
        val cookie = signIn(user)
        clock.advance(Duration.ofMinutes(5))

        repeat(10) { read(user, cookie).andExpect(status().isOk) }
    }

    @Test
    fun `another browser ends the sign-in, and a browser update does not`() {
        val user = createUserWithRole(Role.MEMBER)
        val cookie = signIn(user)

        read(user, cookie, FIREFOX.replace("131.0", "132.0")).andExpect(status().isOk)
        read(user, cookie, CHROME).andExpect(status().isUnauthorized)
        read(user, cookie).andExpect(status().isUnauthorized)
    }

    @Test
    fun `a sign-in used daily is refused on day thirty-one`() {
        val user = createUserWithRole(Role.MEMBER)
        var cookie = signIn(user)

        repeat(29) {
            clock.advance(Duration.ofDays(1))
            cookie = rotatedCookie(user, cookie)
        }
        clock.advance(Duration.ofDays(1))

        read(user, cookie).andExpect(status().isUnauthorized)
    }

    @Test
    fun `a sign-in left alone is refused on day fifteen`() {
        val user = createUserWithRole(Role.MEMBER)
        val cookie = signIn(user)

        clock.advance(Duration.ofDays(14))

        read(user, cookie).andExpect(status().isUnauthorized)
    }

    private companion object {
        const val FIREFOX = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:131.0) Gecko/20100101 Firefox/131.0"
        const val CHROME =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Safari/537.36"
    }
}
