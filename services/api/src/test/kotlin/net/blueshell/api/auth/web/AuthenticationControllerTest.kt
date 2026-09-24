package net.blueshell.api.auth.web

import jakarta.servlet.http.Cookie
import net.blueshell.api.auth.domain.AuthenticationService
import net.blueshell.api.auth.domain.SignInOutcome
import net.blueshell.api.auth.domain.Signer
import net.blueshell.api.auth.domain.twofactor.TrustedBrowsers
import net.blueshell.api.auth.domain.twofactor.TwoFactorStanding
import net.blueshell.api.platform.config.SettableClock
import net.blueshell.api.security.AuthTokenCookieService
import net.blueshell.api.security.Browser
import net.blueshell.api.security.JwtTokenUtil
import net.blueshell.api.security.SignIn
import net.blueshell.api.security.SignIns
import net.blueshell.api.shared.enums.Role
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.time.Duration
import java.time.Instant

class AuthenticationControllerTest {
    private val clock = SettableClock()
    private val service = mock<AuthenticationService>()
    private val cookies = AuthTokenCookieService("BSH_AUTH", "/", "Lax", "", false)
    private val tokens = JwtTokenUtil(
        "2goYh5PqH6dPkWWXLUJQ4QY6nD2YgR5qk9+6Yu8aITR7cfwxkuNolL9zkgf2qHFxifWdbxG+E+XqMIKkt3ibDw==",
        "api",
        "web",
        clock,
    )
    private val signIns = mock<SignIns>()
    private val controller = AuthenticationController(service, cookies, tokens, signIns)
    private val firefox = "Mozilla/5.0 (X11; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0"
    private val standing = TwoFactorStanding(on = true, backupCodesLeft = 9, required = false, offered = false)
    private val signer = Signer(7, "alice", listOf(Role.MEMBER), 3, standing)
    private val signIn = SignIn("s", 7, Instant.EPOCH, Instant.EPOCH, Browser.UNKNOWN, 0, "j", Instant.EPOCH)
    private val signedIn =
        SignInOutcome.SignedIn(
            signer,
            SignIns.Issued(signIn, "the-token", Duration.ofDays(30)),
            TrustedBrowsers.Issued("sel.ver", Duration.ofDays(30)),
        )

    private fun request() = MockHttpServletRequest().apply { addHeader("User-Agent", firefox) }

    private fun MockHttpServletResponse.cookie(name: String) = getHeaders("Set-Cookie").firstOrNull { it.startsWith("$name=") }

    @Test
    fun `a right password answers who signed in and writes the cookies, never the token`() {
        val request = request().apply { setCookies(Cookie(AuthenticationController.TRUSTED_BROWSER_COOKIE, "old.one")) }
        whenever(service.signIn("alice", "pw", Browser("Firefox", "Linux"), "old.one")).thenReturn(signedIn)
        val response = MockHttpServletResponse()

        val answer = controller.authenticate(JwtRequest("alice", "pw"), request, response)

        assertThat(answer).isEqualTo(
            SignInAnswer(
                SignInStatus.SIGNED_IN,
                AuthenticationResponse(7, "alice", listOf(Role.MEMBER), 3, TwoFactorStandingResponse(true, 9, false, false, false)),
            ),
        )
        val login = requireNotNull(answer.login)
        assertThat(listOf(login.username, login.roles, login.twoFactor.on)).containsExactly("alice", listOf(Role.MEMBER), true)
        assertThat(response.cookie("BSH_AUTH")).startsWith("BSH_AUTH=the-token")
        assertThat(response.cookie(AuthenticationController.TRUSTED_BROWSER_COOKIE)).startsWith("BSH_TRUSTED_BROWSER=sel.ver")
    }

    @Test
    fun `with two-factor the password step answers only that a code comes next`() {
        whenever(service.signIn("alice", "pw", Browser("Firefox", "Linux"), null)).thenReturn(SignInOutcome.Challenged("c-1"))
        val response = MockHttpServletResponse()

        assertThat(controller.authenticate(JwtRequest("alice", "pw"), request(), response))
            .isEqualTo(SignInAnswer(SignInStatus.TWO_FACTOR_REQUIRED))
        assertThat(response.cookie(AuthenticationController.CHALLENGE_COOKIE)).contains("c-1").contains("Max-Age=300")
        assertThat(response.cookie("BSH_AUTH")).isNull()
    }

    @Test
    fun `the code step and re-enrolment answer a sign-in, and the challenge cookie goes`() {
        val request = request().apply { setCookies(Cookie(AuthenticationController.CHALLENGE_COOKIE, "c-1")) }
        whenever(service.answerChallenge("c-1", "123456", Browser("Firefox", "Linux"), true)).thenReturn(signedIn)
        whenever(service.reenrol("alice", "pw", "sel.ver", Browser("Firefox", "Linux"))).thenReturn(signedIn)
        val response = MockHttpServletResponse()
        val code = TwoFactorCodeRequest("123456", trustThisBrowser = true)
        val reenrol = ReenrolRequest("sel.ver", "alice", "pw")

        assertThat(controller.answerChallenge(code, request, response).status).isEqualTo(SignInStatus.SIGNED_IN)
        assertThat(response.cookie(AuthenticationController.CHALLENGE_COOKIE)).contains("Max-Age=0")
        assertThat(controller.reenrol(reenrol, request(), MockHttpServletResponse()).login?.userId).isEqualTo(7)
        assertThat(TwoFactorCodeRequest("1").trustThisBrowser).isFalse()
    }

    @Test
    fun `logout ends the sign-in the cookie names and clears it`() {
        val token = tokens.mint("7", "s", "j", clock.instant().plusSeconds(60))
        val response = MockHttpServletResponse()

        controller.logout(MockHttpServletRequest().apply { setCookies(Cookie("BSH_AUTH", token)) }, response)
        controller.logout(MockHttpServletRequest(), MockHttpServletResponse())

        verify(signIns).end("s")
        assertThat(response.cookie("BSH_AUTH")).contains("Max-Age=0")
    }
}
