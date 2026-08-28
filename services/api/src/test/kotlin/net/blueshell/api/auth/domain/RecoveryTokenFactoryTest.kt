package net.blueshell.api.auth.domain

import net.blueshell.api.auth.persistence.RecoveryToken
import net.blueshell.api.auth.persistence.RecoveryTokenRepository
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.enums.TokenPurpose
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Duration
import java.time.Instant

class RecoveryTokenFactoryTest {

    private val repository = mock<RecoveryTokenRepository>()
    private val encoder = mock<PasswordEncoder>()
    private val factory = RecoveryTokenFactory(repository, encoder)

    private fun user(id: Long = 1L): User {
        val user = mock<User>()
        whenever(user.id).thenReturn(id)
        return user
    }

    @Test
    fun `issue deletes existing unconsumed tokens of same type for the user`() {
        val existingToken = mock<RecoveryToken>()
        whenever(repository.findAllUnconsumedByTypeAndUserId(1L, TokenPurpose.PASSWORD_RESET))
            .thenReturn(mutableListOf(existingToken))
        whenever(encoder.encode(any())).thenReturn("hashed-verifier")
        whenever(repository.save(any<RecoveryToken>())).thenAnswer { it.arguments[0] }

        factory.issue(user(), TokenPurpose.PASSWORD_RESET, Duration.ofHours(24))

        verify(repository).delete(existingToken)
    }

    @Test
    fun `issue creates token with hashed verifier and correct expiry`() {
        whenever(repository.findAllUnconsumedByTypeAndUserId(any(), any()))
            .thenReturn(mutableListOf())
        whenever(encoder.encode(any())).thenReturn("hashed-verifier")
        whenever(repository.save(any<RecoveryToken>())).thenAnswer { it.arguments[0] }

        val ttl = Duration.ofHours(24)
        val beforeIssue = Instant.now()

        factory.issue(user(), TokenPurpose.PASSWORD_RESET, ttl)

        verify(repository).save(argThat<RecoveryToken> { token ->
            token.verifierHash == "hashed-verifier" &&
                token.type == TokenPurpose.PASSWORD_RESET &&
                token.expiresAt.isAfter(beforeIssue.plus(ttl).minusSeconds(5))
        })
    }

    @Test
    fun `issue returns raw token in selector dot verifier format`() {
        whenever(repository.findAllUnconsumedByTypeAndUserId(any(), any()))
            .thenReturn(mutableListOf())
        whenever(encoder.encode(any())).thenReturn("hashed")
        whenever(repository.save(any<RecoveryToken>())).thenAnswer { it.arguments[0] }

        val rawToken = factory.issue(user(), TokenPurpose.PASSWORD_RESET, Duration.ofHours(1))

        assertThat(rawToken).contains(".")
        val parts = rawToken.split(".")
        assertThat(parts).hasSize(2)
        assertThat(parts[0]).isNotBlank()
        assertThat(parts[1]).isNotBlank()
    }

    @Test
    fun `consume sets consumedAt timestamp on the token`() {
        val token = RecoveryToken(
            user = user(),
            type = TokenPurpose.PASSWORD_RESET,
            selector = "sel",
            verifierHash = "hash",
            expiresAt = Instant.now().plusSeconds(3600),
        )
        whenever(repository.save(any<RecoveryToken>())).thenAnswer { it.arguments[0] }

        assertThat(token.consumedAt).isNull()

        factory.consume(token)

        assertThat(token.consumedAt).isNotNull()
        verify(repository).save(token)
    }
}
