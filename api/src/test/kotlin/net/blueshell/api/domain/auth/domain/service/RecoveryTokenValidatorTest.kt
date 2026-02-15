package net.blueshell.api.domain.auth.domain.service

import net.blueshell.api.domain.auth.application.exception.ConsumedRecoveryTokenException
import net.blueshell.api.domain.auth.application.exception.ExpiredRecoveryTokenException
import net.blueshell.api.domain.auth.application.exception.InvalidRecoveryTokenException
import net.blueshell.api.domain.auth.application.exception.InvalidTokenTypeException
import net.blueshell.api.domain.auth.application.exception.MalformedRecoveryTokenException
import net.blueshell.api.domain.auth.application.exception.TokenVerificationFailedException
import net.blueshell.api.domain.auth.persistence.RecoveryToken
import net.blueshell.api.domain.auth.persistence.repository.RecoveryTokenRepository
import net.blueshell.api.shared.enums.ResetType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant
import java.util.Optional

class RecoveryTokenValidatorTest {

    private val repository = mock<RecoveryTokenRepository>()
    private val encoder = mock<PasswordEncoder>()
    private val validator = RecoveryTokenValidator(repository, encoder)

    @Test
    fun `throws malformed exception for invalid raw token format`() {
        assertThrows<MalformedRecoveryTokenException> {
            validator.verify("invalid-token", ResetType.PASSWORD_RESET)
        }
    }

    @Test
    fun `throws when selector is not found`() {
        whenever(repository.findBySelector("selector")).thenReturn(Optional.empty())

        assertThrows<InvalidRecoveryTokenException> {
            validator.verify("selector.verifier", ResetType.PASSWORD_RESET)
        }
    }

    @Test
    fun `throws when token type does not match expected`() {
        val token = token(type = ResetType.USER_ACTIVATION)
        whenever(repository.findBySelector("selector")).thenReturn(Optional.of(token))

        assertThrows<InvalidTokenTypeException> {
            validator.verify("selector.verifier", ResetType.PASSWORD_RESET)
        }
    }

    @Test
    fun `throws when token is expired`() {
        val token = token(expiresAt = Instant.now().minusSeconds(60))
        whenever(repository.findBySelector("selector")).thenReturn(Optional.of(token))

        assertThrows<ExpiredRecoveryTokenException> {
            validator.verify("selector.verifier", ResetType.PASSWORD_RESET)
        }
    }

    @Test
    fun `throws when token is consumed`() {
        val token = token(consumedAt = Instant.now())
        whenever(repository.findBySelector("selector")).thenReturn(Optional.of(token))

        assertThrows<ConsumedRecoveryTokenException> {
            validator.verify("selector.verifier", ResetType.PASSWORD_RESET)
        }
    }

    @Test
    fun `throws when verifier does not match hash`() {
        val token = token()
        whenever(repository.findBySelector("selector")).thenReturn(Optional.of(token))
        whenever(encoder.matches("verifier", "hash")).thenReturn(false)

        assertThrows<TokenVerificationFailedException> {
            validator.verify("selector.verifier", ResetType.PASSWORD_RESET)
        }
    }

    @Test
    fun `returns token when verification succeeds`() {
        val token = token()
        whenever(repository.findBySelector("selector")).thenReturn(Optional.of(token))
        whenever(encoder.matches("verifier", "hash")).thenReturn(true)

        val result = validator.verify("selector.verifier", ResetType.PASSWORD_RESET)

        assertThat(result).isSameAs(token)
    }

    @Test
    fun `returns unconsumed tokens by user id`() {
        val tokens = mutableListOf(token(), token(selector = "other"))
        whenever(repository.findAllUnconsumedByUserId(7)).thenReturn(tokens)

        val result = validator.findUnconsumedByUserId(7)

        assertThat(result).isEqualTo(tokens)
    }

    private fun token(
        selector: String = "selector",
        type: ResetType = ResetType.PASSWORD_RESET,
        expiresAt: Instant = Instant.now().plusSeconds(3600),
        consumedAt: Instant? = null
    ): RecoveryToken {
        return RecoveryToken().apply {
            this.selector = selector
            this.type = type
            this.verifierHash = "hash"
            this.expiresAt = expiresAt
            this.consumedAt = consumedAt
        }
    }
}
