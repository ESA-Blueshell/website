package net.blueshell.api.platform.web

import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import net.blueshell.api.platform.integration.mock.InMemoryEmailClient
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import net.blueshell.api.auth.domain.twofactor.TrustedBrowsers
import net.blueshell.api.auth.domain.twofactor.TwoFactor
import net.blueshell.api.auth.web.AuthenticationController
import net.blueshell.api.platform.config.SettableClock
import net.blueshell.api.security.AuthTokenCookieService
import net.blueshell.api.security.Browser
import net.blueshell.api.security.SignIn
import net.blueshell.api.security.SignInContext
import net.blueshell.api.security.SignIns
import net.blueshell.api.user.api.UserService
import org.springframework.http.HttpHeaders
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Duration
import java.time.Instant

/**
 * Test-only HTTP surface for the system-tests project. Mounted under
 * `@Profile("test")` so production deployments never see the endpoint.
 *
 * Exposes the in-memory outbox the `InMemoryEmailClient` collects
 * during test runs so system tests can assert "the api dispatched an
 * email with subject X to recipient Y" without reaching into the bean
 * graph from a Spring-free test JVM.
 */
@RestController
@RequestMapping("/test-support")
@Profile("test")
@Hidden
@Tag(name = "Test Support")
class TestSupportController(
    private val emailClient: InMemoryEmailClient,
    private val clock: SettableClock,
    private val users: UserService,
    private val twoFactor: TwoFactor,
    private val trustedBrowsers: TrustedBrowsers,
    private val cookies: AuthTokenCookieService,
    private val signIns: SignIns,
) {
    @GetMapping("/emails")
    @PermitAll
    fun listEmails(
        @RequestParam(required = false) recipient: String?,
        @RequestParam(required = false) subject: String?,
    ): List<InMemoryEmailClient.SentEmail> =
        emailClient.sentEmails.filter { email ->
            (recipient == null || email.toEmail.equals(recipient, ignoreCase = true)) &&
                (subject == null || email.subject == subject)
        }

    /**
     * Readies [username] for a system test's sign-in helper: the one-time offer is answered, and a
     * person with two-factor gets a trusted-browser cookie for the calling browser, so the helper
     * signs in with the password alone. Tests of two-factor itself sign in without this.
     */
    @PostMapping("/sign-in-ready")
    @PermitAll
    fun signInReady(
        @RequestParam username: String,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): Map<String, String?> {
        val user = users.findByUsername(username)
        val userId = requireNotNull(user.id)
        twoFactor.answerOffer(userId)
        if (!user.hasTwoFactor) return mapOf("trustedBrowser" to null)
        val trusted = trustedBrowsers.trust(userId, Browser.of(request.getHeader(HttpHeaders.USER_AGENT)))
        cookies.writeCookie(response, AuthenticationController.TRUSTED_BROWSER_COOKIE, trusted.cookieValue, trusted.ttl.toMillis())
        return mapOf("trustedBrowser" to trusted.cookieValue)
    }

    /**
     * Counts the calling sign-in as proved just now, as a code would, for a system test whose
     * subject is something behind a step-up rather than the step-up itself.
     */
    @PostMapping("/step-up")
    @PermitAll
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun stepUp() {
        val signIn = SignInContext.current() ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        signIns.recordStepUp(signIn.id, SignIn.METHOD_OTP)
    }

    /** Stops the api's clock at [instant], so a system test can step across a time rule. */
    @PutMapping("/clock")
    @PermitAll
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun setClock(@RequestParam instant: Instant) = clock.set(instant)

    @PostMapping("/clock/advance")
    @PermitAll
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun advanceClock(@RequestParam seconds: Long) = clock.advance(Duration.ofSeconds(seconds))

    @DeleteMapping("/clock")
    @PermitAll
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun resetClock() = clock.reset()
}
