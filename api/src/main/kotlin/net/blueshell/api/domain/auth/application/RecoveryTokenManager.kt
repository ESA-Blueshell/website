package net.blueshell.api.domain.auth.application

import net.blueshell.api.domain.auth.application.exception.InvalidRecoveryTokenException
import net.blueshell.api.domain.auth.persistence.RecoveryToken
import net.blueshell.api.domain.auth.persistence.repository.RecoveryTokenRepository
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.enums.ResetType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.function.Predicate
import java.util.function.Supplier

@Service
class RecoveryTokenManager(
    private val repository: RecoveryTokenRepository,
    private val encoder: PasswordEncoder
) {
    private val random = SecureRandom()

    @Transactional
    fun issue(user: User, type: ResetType, ttl: Duration): String {
        repository.findAllUnconsumedByTypeAndUserId(user.id!!, type)
            .forEach { repository.delete(it) }

        val selector = randomUrlSafe(16) // 128-bit
        val verifier = randomUrlSafe(32) // 256-bit

        val token = RecoveryToken()
        token.user = user
        token.type = type
        token.selector = selector
        token.verifierHash = encoder.encode(verifier)
        token.expiresAt = Instant.now().plus(ttl)

        repository.save(token)
        return "$selector.$verifier"
    }

    @Transactional(readOnly = true)
    fun verify(rawToken: String, expectedType: ResetType): RecoveryToken {
        val parts: Array<String> = rawToken.split("\\.".toRegex(), limit = 2).toTypedArray()
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw InvalidRecoveryTokenException()
        }
        val selector = parts[0]
        val verifier = parts[1]

        val token = repository.findBySelector(selector)
            .filter(Predicate { t: RecoveryToken -> t.type == expectedType })
            .orElseThrow(Supplier { InvalidRecoveryTokenException() })

        if (token.isConsumed || token.isExpired) {
            throw InvalidRecoveryTokenException()
        }
        if (!encoder.matches(verifier, token.verifierHash)) {
            throw InvalidRecoveryTokenException()
        }
        return token
    }

    @Transactional
    fun consume(token: RecoveryToken) {
        token.consumedAt = Instant.now()
        repository.save(token)
    }

    @Transactional(readOnly = true)
    fun findUnconsumedByUserId(userId: Long): MutableList<RecoveryToken> {
        return repository.findAllUnconsumedByUserId(userId)
    }

    private fun randomUrlSafe(numBytes: Int): String {
        val bytes = ByteArray(numBytes)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
