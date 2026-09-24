package net.blueshell.api.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.Cookie
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.UserPrincipal
import net.blueshell.api.platform.config.SettableClock
import net.blueshell.api.testsupport.InMemorySignInStore
import net.blueshell.api.user.api.UserNotFoundException
import net.blueshell.api.user.api.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository
import java.time.Duration
import java.time.Instant

class JwtAuthFilterTest {
    private val clock = SettableClock().apply { set(Instant.parse("2026-09-24T12:00:00Z")) }
    private val tokens =
        JwtTokenUtil("2goYh5PqH6dPkWWXLUJQ4QY6nD2YgR5qk9+6Yu8aITR7cfwxkuNolL9zkgf2qHFxifWdbxG+E+XqMIKkt3ibDw==", "api", "web", clock)
    private val signIns =
        SignIns(
            InMemorySignInStore(),
            tokens,
            clock,
            ApplicationEventPublisher {},
            Duration.ofDays(30),
            Duration.ofDays(14),
            Duration.ofMinutes(5),
            Duration.ofSeconds(60),
        )
    private val userService: UserService = mock()
    private val cookies = AuthTokenCookieService("BSH_AUTH", "/", "Lax", "", false)
    private val filter = JwtAuthFilter(signIns, userService, cookies, RequestAttributeSecurityContextRepository())
    private val chain = FilterChain { _, _ -> }

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.clearContext()
        whenever(userService.loadUserPrincipalById(7)).thenReturn(principal())
    }

    private fun request(
        token: String?,
        uri: String = "/users/7",
    ) = MockHttpServletRequest("GET", uri).apply { token?.let { setCookies(Cookie("BSH_AUTH", it)) } }

    @Test
    fun `a cookie naming a live sign-in signs the request in`() {
        val issued = signIns.start(7, Browser.UNKNOWN)
        val request = request(issued.token)

        filter.doFilter(request, MockHttpServletResponse(), chain)

        assertThat(SecurityContextHolder.getContext().authentication?.name).isEqualTo("alice")
        assertThat((SecurityContextHolder.getContext().authentication?.details as SignInDetails).signInId)
            .isEqualTo(issued.signIn.id)
        assertThat(request.getAttribute(SignInContext.ATTRIBUTE)).isEqualTo(issued.signIn)
    }

    @Test
    fun `a bearer header is not a credential`() {
        val issued = signIns.start(7, Browser.UNKNOWN)
        val request = request(null).apply { addHeader("Authorization", "Bearer ${issued.token}") }

        filter.doFilter(request, MockHttpServletResponse(), chain)

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
    }

    @Test
    fun `a cookie due for rotation is answered with a new one`() {
        val issued = signIns.start(7, Browser.UNKNOWN)
        clock.advance(Duration.ofMinutes(5))
        val response = MockHttpServletResponse()

        filter.doFilter(request(issued.token), response, chain)

        assertThat(response.getHeader("Set-Cookie")).startsWith("BSH_AUTH=").doesNotContain(issued.token)
    }

    @Test
    fun `forward-auth never rotates, because Traefik keeps the answer's cookies to itself`() {
        val issued = signIns.start(7, Browser.UNKNOWN)
        clock.advance(Duration.ofMinutes(5))
        val response = MockHttpServletResponse()

        filter.doFilter(request(issued.token, "/oauth2/forward-auth"), response, chain)

        assertThat(SecurityContextHolder.getContext().authentication).isNotNull
        assertThat(response.getHeader("Set-Cookie")).isNull()
    }

    @Test
    fun `a sign-in whose person is gone leaves the request anonymous`() {
        val issued = signIns.start(7, Browser.UNKNOWN)
        whenever(userService.loadUserPrincipalById(7)).thenThrow(UserNotFoundException("gone"))

        filter.doFilter(request(issued.token), MockHttpServletResponse(), chain)

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
    }

    @Test
    fun `an ended sign-in leaves the request anonymous`() {
        val issued = signIns.start(7, Browser.UNKNOWN)
        signIns.end(issued.signIn.id)

        filter.doFilter(request(issued.token), MockHttpServletResponse(), chain)

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
    }

    private fun principal() = UserPrincipal(7, "alice", "hash", true, setOf(Role.MEMBER), null, null)
}
