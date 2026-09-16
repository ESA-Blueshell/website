package net.blueshell.api.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import net.blueshell.api.auth.api.TokenGenerator
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.time.Duration

class AuthTokenRenewalServiceTest {

    private val lifetime = Duration.ofDays(30)
    private val tokenGenerator: TokenGenerator = mock()
    private val authTokenCookieService: AuthTokenCookieService = mock()
    private val request: HttpServletRequest = mock()
    private val response: HttpServletResponse = mock()

    private lateinit var service: AuthTokenRenewalService

    @BeforeEach
    fun setUp() {
        whenever(tokenGenerator.expirationMs).thenReturn(lifetime.toMillis())
        whenever(request.requestURI).thenReturn("/events")
        whenever(response.isCommitted).thenReturn(false)
        service = AuthTokenRenewalService(tokenGenerator, authTokenCookieService, Duration.ofDays(1))
    }

    /** Remaining life, expressed as the instant the token says it stops being honoured. */
    private fun expiringIn(remaining: Duration): Long = System.currentTimeMillis() + remaining.toMillis()

    @Test
    fun `a token past the threshold is re-issued for a full lifetime`() {
        whenever(tokenGenerator.generateToken("alice")).thenReturn("fresh-token")

        service.renewIfDue(request, response, "alice", expiringIn(Duration.ofDays(28)))

        verify(authTokenCookieService).writeAuthCookie(response, "fresh-token", lifetime.toMillis())
    }

    @Test
    fun `a token minted moments ago is left alone`() {
        service.renewIfDue(request, response, "alice", expiringIn(Duration.ofDays(30)))

        verifyNoInteractions(authTokenCookieService)
    }

    @Test
    fun `the paths that issue and retire the cookie are not written over`() {
        service.renewIfDue(requestAt("/auth"), response, "alice", expiringIn(Duration.ofDays(1)))
        service.renewIfDue(requestAt("/auth/logout"), response, "alice", expiringIn(Duration.ofDays(1)))

        verifyNoInteractions(authTokenCookieService)
    }

    @Test
    fun `a committed response takes no cookie`() {
        whenever(response.isCommitted).thenReturn(true)

        service.renewIfDue(request, response, "alice", expiringIn(Duration.ofDays(1)))

        verifyNoInteractions(authTokenCookieService)
    }

    @Test
    fun `a token that never said when it expires is left alone`() {
        service.renewIfDue(request, response, "alice", null)

        verifyNoInteractions(authTokenCookieService)
    }

    private fun requestAt(path: String): HttpServletRequest =
        mock<HttpServletRequest>().also { whenever(it.requestURI).thenReturn(path) }
}
