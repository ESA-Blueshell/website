package net.blueshell.api.security

import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import java.time.Duration

/**
 * A sign-in has one length, and three things express it: the token, the auth cookie written from
 * the token, and the http session beside them. They used to disagree — a 24h token next to a
 * thirty-day session — and the shortest of them decided, so a reader was turned away a day into a
 * session the api would still have answered. This says they are the same number.
 */
@SpringBootTest
class SignInLifetimeIT : UserTestSupport() {

    @Value($$"${app.jwt.expiration}")
    private lateinit var tokenLifetime: Duration

    @Value($$"${session.timeout}")
    private lateinit var sessionTimeout: Duration

    @Value($$"${app.jwt.renew-after}")
    private lateinit var renewAfter: Duration

    @Test
    fun `the token and the session last the same thirty days`() {
        assertThat(tokenLifetime).isEqualTo(Duration.ofDays(30))
        assertThat(sessionTimeout).isEqualTo(tokenLifetime)
    }

    @Test
    fun `the generator mints for that same length`() {
        assertThat(tokenGenerator.expirationMs).isEqualTo(Duration.ofDays(30).toMillis())
    }

    @Test
    fun `a token is re-issued well inside its own life, never after it has lapsed`() {
        assertThat(renewAfter).isLessThan(tokenLifetime)
    }
}
