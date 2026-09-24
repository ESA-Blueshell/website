package net.blueshell.api.auth.domain

import net.blueshell.api.auth.persistence.RecoveryToken
import net.blueshell.api.auth.persistence.RecoveryTokenRepository
import net.blueshell.api.shared.enums.TokenPurpose
import net.blueshell.api.user.persistence.User
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.util.Base64

/**
 * Factory responsible for creating and persisting recovery tokens.
 */
@Component
class RecoveryTokenFactory(
    private val repository: RecoveryTokenRepository,
    private val encoder: PasswordEncoder,
    private val clock: Clock,
) {
    private val random = SecureRandom()

    /**
     * A fresh `selector.verifier` token, dropping any unconsumed token of the same type the user
     * already holds — except a lock link, which never retires another (api ADR-031).
     */
    @Transactional
    fun issue(
        user: User,
        type: TokenPurpose,
        ttl: Duration,
    ): String {
        if (type.retiresEarlier) {
            repository
                .findAllUnconsumedByTypeAndUserId(user.id!!, type)
                .forEach { repository.delete(it) }
        }

        val selector = randomUrlSafe(16) // 128-bit
        val verifier = randomUrlSafe(32) // 256-bit

        val token =
            RecoveryToken(
                user = user,
                type = type,
                selector = selector,
                verifierHash = requireNotNull(encoder.encode(verifier)) { "PasswordEncoder returned null verifier hash" },
                expiresAt = clock.instant().plus(ttl),
            )

        repository.save(token)
        return "$selector.$verifier"
    }

    /**
     * Mark a token as consumed.
     */
    @Transactional
    fun consume(token: RecoveryToken) {
        token.consumedAt = clock.instant()
        repository.save(token)
    }

    private fun randomUrlSafe(numBytes: Int): String {
        val bytes = ByteArray(numBytes)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
