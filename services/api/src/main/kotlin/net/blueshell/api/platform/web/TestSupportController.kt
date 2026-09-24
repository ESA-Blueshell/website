package net.blueshell.api.platform.web

import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import net.blueshell.api.platform.integration.mock.InMemoryEmailClient
import net.blueshell.api.platform.config.SettableClock
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
