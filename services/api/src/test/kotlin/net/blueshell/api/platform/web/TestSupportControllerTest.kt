package net.blueshell.api.platform.web

import net.blueshell.api.auth.domain.twofactor.TrustedBrowsers
import net.blueshell.api.auth.domain.twofactor.TwoFactor
import net.blueshell.api.platform.config.SettableClock
import net.blueshell.api.platform.integration.mock.InMemoryEmailClient
import net.blueshell.api.security.AuthTokenCookieService
import net.blueshell.api.security.Browser
import net.blueshell.api.security.SignIn
import net.blueshell.api.security.SignInContext
import net.blueshell.api.security.SignIns
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.server.ResponseStatusException
import java.time.Duration
import java.time.Instant

class TestSupportControllerTest {
    private val clock = SettableClock()
    private val users = mock<UserService>()
    private val twoFactor = mock<TwoFactor>()
    private val trustedBrowsers = mock<TrustedBrowsers>()
    private val signIns = mock<SignIns>()
    private val controller =
        TestSupportController(
            mock<InMemoryEmailClient>(),
            clock,
            users,
            twoFactor,
            trustedBrowsers,
            AuthTokenCookieService("BSH_AUTH", "/", "Lax", "", false),
            signIns,
        )
    private val user =
        User(
            username = "alice",
            email = "a@example.com",
            password = "h",
            initials = "A",
            firstName = "A",
            lastName = "D",
        ).also { it.id = 7 }

    @AfterEach
    fun tearDown() = RequestContextHolder.resetRequestAttributes()

    @Test
    fun `readying a sign-in answers the offer, and trusts this browser for somebody with two-factor`() {
        whenever(users.findByUsername("alice")).thenReturn(user)
        val request = MockHttpServletRequest().apply { addHeader("User-Agent", "Mozilla/5.0 (X11; Linux x86_64; rv:131.0) Firefox/131.0") }

        assertThat(controller.signInReady("alice", request, MockHttpServletResponse())).containsEntry("trustedBrowser", null)
        verify(trustedBrowsers, never()).trust(7, Browser("Firefox", "Linux"))

        user.twoFactorSince = Instant.EPOCH
        whenever(trustedBrowsers.trust(7, Browser("Firefox", "Linux"))).thenReturn(TrustedBrowsers.Issued("sel.ver", Duration.ofDays(30)))
        val response = MockHttpServletResponse()
        assertThat(controller.signInReady("alice", request, response)).containsEntry("trustedBrowser", "sel.ver")
        assertThat(response.getHeader("Set-Cookie")).startsWith("BSH_TRUSTED_BROWSER=sel.ver")
        verify(twoFactor, times(2)).answerOffer(7)
    }

    @Test
    fun `a step-up counts the calling sign-in as proved, and needs one`() {
        assertThrows<ResponseStatusException> { controller.stepUp() }

        val signIn = SignIn("s", 7, Instant.EPOCH, Instant.EPOCH, Browser.UNKNOWN, 0, "j", Instant.EPOCH)
        val request = MockHttpServletRequest().apply { setAttribute(SignInContext.ATTRIBUTE, signIn) }
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
        controller.stepUp()

        verify(signIns).recordStepUp("s", SignIn.METHOD_OTP)
    }

    @Test
    fun `the clock stops, moves and runs again`() {
        controller.setClock(Instant.parse("2026-09-24T12:00:00Z"))
        controller.advanceClock(60)
        assertThat(clock.instant()).isEqualTo(Instant.parse("2026-09-24T12:01:00Z"))

        controller.resetClock()
        assertThat(clock.instant()).isNotEqualTo(Instant.parse("2026-09-24T12:01:00Z"))
    }
}
