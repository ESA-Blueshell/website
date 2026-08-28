package net.blueshell.api.auth.web

import net.blueshell.api.auth.domain.ConsumedRecoveryTokenException
import net.blueshell.api.auth.domain.InvalidRecoveryTokenException
import net.blueshell.api.auth.domain.TokenVerificationFailedException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.BadCredentialsException

class AuthProblemDetailsAdviceTest {

    private val advice = AuthProblemDetailsAdvice()

    @Test
    fun `invalid recovery token response does not expose internal message`() {
        val request = MockHttpServletRequest("POST", "/recovery/password")
        val detail = advice.handleInvalidRecoveryToken(
            InvalidRecoveryTokenException("Recovery token not found in datastore"),
            request
        )

        assertThat(detail.status).isEqualTo(HttpStatus.NOT_FOUND.value())
        assertThat(detail.detail).isEqualTo("Invalid or expired recovery token.")
    }

    @Test
    fun `specific token failures are mapped to generic detail`() {
        val request = MockHttpServletRequest("POST", "/recovery/password")
        val consumed = advice.handleSpecificRecoveryTokenExceptions(
            ConsumedRecoveryTokenException("Token already consumed at timestamp ..."),
            request
        )
        val mismatch = advice.handleSpecificRecoveryTokenExceptions(
            TokenVerificationFailedException("Verifier mismatch for selector xyz"),
            request
        )

        assertThat(consumed.status).isEqualTo(HttpStatus.BAD_REQUEST.value())
        assertThat(consumed.detail).isEqualTo("Invalid or expired recovery token.")
        assertThat(mismatch.status).isEqualTo(HttpStatus.BAD_REQUEST.value())
        assertThat(mismatch.detail).isEqualTo("Invalid or expired recovery token.")
    }

    @Test
    fun `authentication failures return generic unauthorized detail`() {
        val request = MockHttpServletRequest("POST", "/auth")
        val detail = advice.handleAuthenticationException(
            BadCredentialsException("User account is disabled"),
            request
        )

        assertThat(detail.status).isEqualTo(HttpStatus.UNAUTHORIZED.value())
        assertThat(detail.detail).isEqualTo("Invalid username or password.")
    }
}
