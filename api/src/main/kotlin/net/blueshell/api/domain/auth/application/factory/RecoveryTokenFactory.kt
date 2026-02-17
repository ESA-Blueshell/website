package net.blueshell.api.domain.auth.application.factory

import net.blueshell.api.domain.auth.persistence.RecoveryToken
import net.blueshell.api.domain.auth.persistence.repository.RecoveryTokenRepository
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.enums.ResetType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.*

/**
 * Factory responsible for creating and persisting recovery tokens.
 */
@Component
class RecoveryTokenFactory(
    private val repository: RecoveryTokenRepository,
    private val encoder: PasswordEncoder
) {
    private val random = SecureRandom()

    /**
     * Issue a new recovery token for a user.
     * Deletes any existing unconsumed tokens of the same type for the user.
     *
     * @param user The user for whom to create the token
     * @param type The type of recovery operation
     * @param ttl Time-to-live for the token
     * @return The raw token string in format "selector.verifier"
     */
    @Transactional
    fun issue(user: User, type: ResetType, ttl: Duration): String {
        // Delete any existing unconsumed tokens of this type
        repository.findAllUnconsumedByTypeAndUserId(user.id!!, type)
            .forEach { repository.delete(it) }

        val selector = randomUrlSafe(16) // 128-bit
        val verifier = randomUrlSafe(32) // 256-bit

        val token = RecoveryToken(
            user = user,
            type = type,
            selector = selector,
            verifierHash = encoder.encode(verifier),
            expiresAt = Instant.now().plus(ttl),
        )

        repository.save(token)
        return "$selector.$verifier"
    }

    /**
     * Mark a token as consumed.
     */
    @Transactional
    fun consume(token: RecoveryToken) {
        token.consumedAt = Instant.now()
        repository.save(token)
    }

    private fun randomUrlSafe(numBytes: Int): String {
        val bytes = ByteArray(numBytes)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
