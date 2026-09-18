package net.blueshell.api.security

import net.blueshell.api.factory.auth.web.request.AuthRequestFactory
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration

/**
 * A sign-in has one length, and three things express it: the token, the auth cookie written from
 * the token, and the http session beside them. They used to disagree — a 24h token next to a
 * thirty-day session — and the shortest of them decided, so a reader was turned away a day into a
 * session the api would still have answered. This says they are the same number.
 */
@SpringBootTest
class SignInLifetimeIT : UserTestSupport() {
    @Autowired
    private lateinit var authRequestFactory: AuthRequestFactory

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

    /**
     * The other half of the arrangement, asserted rather than assumed: once the auth cookie is
     * gone, the `SESSION` cookie is what carries the sign-in, and it carries it on its own.
     *
     * This is the claim the whole thirty days rests on — `JwtAuthFilter` returns early on a token
     * it cannot use and leaves whatever `HttpSessionSecurityContextRepository` restored in place.
     * A browser test cannot ask it without also asking about cookie attributes and proxies, so it
     * is asked here, of the api alone.
     */
    @Test
    fun `the session cookie answers on its own once the auth cookie is gone`() {
        val user = createUserWithRole(Role.MEMBER)

        val signIn =
            mvc
                .perform(
                    post("/auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authRequestFactory.authenticatePayload(user.username, "Password123!")),
                ).andExpect(status().isOk)
                .andReturn()

        val authCookie = signIn.response.cookies.first { it.name == "BSH_AUTH" }

        // A read that authenticates off the auth cookie, which is what puts the security context
        // into the session in the first place.
        val warmed =
            mvc
                .perform(get("/users/${user.id}").cookie(authCookie))
                .andExpect(status().isOk)
                .andReturn()

        val session = warmed.request.getSession(false)
        assertThat(session).describedAs("a session to carry the sign-in").isNotNull

        // The same read with the auth cookie withheld, carrying only the session.
        mvc
            .perform(get("/users/${user.id}").session(session as MockHttpSession))
            .andExpect(status().isOk)
    }
}
