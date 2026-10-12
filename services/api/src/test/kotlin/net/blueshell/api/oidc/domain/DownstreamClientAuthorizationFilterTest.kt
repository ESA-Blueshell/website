package net.blueshell.api.oidc.domain

import net.blueshell.api.security.Browser
import net.blueshell.api.security.SignIn
import net.blueshell.api.security.SignInContext
import net.blueshell.api.security.SignIns
import net.blueshell.api.security.StepUp
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.UserPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.time.Instant

class DownstreamClientAuthorizationFilterTest {
    private val signIns = mock<SignIns>()
    private val filter = DownstreamClientAuthorizationFilter(signIns)
    private val signIn = SignIn("s", 1, Instant.EPOCH, Instant.EPOCH, Browser.UNKNOWN, 0, "j", Instant.EPOCH)

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
        RequestContextHolder.resetRequestAttributes()
    }

    private fun signedInAs(
        role: Role,
        twoFactor: Boolean,
    ) {
        val principal = UserPrincipal(1, "root", "h", true, setOf(role), null, null, hasTwoFactor = twoFactor)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
    }

    private fun authorize(signIn: SignIn? = null): Pair<MockHttpServletResponse, MockFilterChain> {
        val request = MockHttpServletRequest("GET", "/oauth2/authorize").apply { setParameter("client_id", "vault") }
        signIn?.let { request.setAttribute(SignInContext.ATTRIBUTE, it) }
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()
        filter.doFilter(request, response, chain)
        return response to chain
    }

    @Test
    fun `only the authorize endpoint is gated`() {
        val chain = MockFilterChain()
        filter.doFilter(MockHttpServletRequest("GET", "/oauth2/token"), MockHttpServletResponse(), chain)

        assertThat(chain.request).isNotNull()
    }

    @Test
    fun `somebody not signed in goes on to the login entry point`() {
        assertThat(authorize().second.request).isNotNull()

        SecurityContextHolder.getContext().authentication =
            AnonymousAuthenticationToken("k", "anon", listOf(SimpleGrantedAuthority("ROLE_ANONYMOUS")))
        assertThat(authorize().second.request).isNotNull()

        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken("root", "pw")
        assertThat(authorize().second.request).isNotNull()
    }

    @Test
    fun `anybody but an admin is refused`() {
        signedInAs(Role.BOARD, twoFactor = true)

        val (response, chain) = authorize(signIn)

        assertThat(response.status).isEqualTo(403)
        assertThat(chain.request).isNull()
    }

    @Test
    fun `an admin with two-factor proves it again unless the sign-in did so lately`() {
        signedInAs(Role.ADMIN, twoFactor = true)

        val (unproved, stopped) = authorize()
        assertThat(unproved.redirectedUrl).startsWith("/login?stepUp=1&redirect=%2Fapi%2Foauth2%2Fauthorize%3Fclient_id%3Dvault")
        assertThat(stopped.request).isNull()

        assertThat(authorize(signIn).first.redirectedUrl).startsWith("/login?stepUp=1")

        whenever(signIns.steppedUpWithin(signIn, StepUp.WINDOW)).thenReturn(true)
        assertThat(authorize(signIn).second.request).isNotNull()
    }

    @Test
    fun `an admin without two-factor goes on`() {
        signedInAs(Role.ADMIN, twoFactor = false)

        assertThat(authorize().second.request).isNotNull()
    }
}
