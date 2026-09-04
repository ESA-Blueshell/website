package net.blueshell.api.auth.domain

import net.blueshell.api.auth.domain.*
import net.blueshell.api.auth.persistence.RecoveryToken
import net.blueshell.api.auth.persistence.RecoveryTokenRepository
import net.blueshell.api.shared.enums.TokenPurpose
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** Validates recovery tokens. */
@Component
class RecoveryTokenValidator(
    private val repository: RecoveryTokenRepository,
    private val encoder: PasswordEncoder
) {

    /** Ids of accounts holding an unconsumed token of this kind. */
    @Transactional(readOnly = true)
    fun findUserIdsWithUnconsumedType(type: TokenPurpose): Set<Long> =
        repository.findUserIdsWithUnconsumedType(type).toSet()

    /** The token a raw `selector.verifier` string names, or a reason it is unusable. */
    @Transactional(readOnly = true)
    fun verify(rawToken: String, expectedType: TokenPurpose): RecoveryToken {
        val validation = try {
            RecoveryTokenValidation.fromRawToken(rawToken, expectedType)
        } catch (e: IllegalArgumentException) {
            throw MalformedRecoveryTokenException(e.message ?: "Invalid token format")
        }

        val token = repository.findBySelector(validation.selector)
            .orElseThrow { InvalidRecoveryTokenException("Recovery token not found") }

        if (token.type != expectedType) {
            throw InvalidTokenTypeException("Token type ${token.type} does not match expected type $expectedType")
        }

        if (token.isExpired) {
            throw ExpiredRecoveryTokenException("Recovery token has expired")
        }

        if (token.isConsumed) {
            throw ConsumedRecoveryTokenException("Recovery token has already been used")
        }

        if (!encoder.matches(validation.verifier, token.verifierHash)) {
            throw TokenVerificationFailedException("Recovery token verification failed")
        }

        return token
    }

    /** Every unconsumed token a user holds. */
    @Transactional(readOnly = true)
    fun findUnconsumedByUserId(userId: Long): List<RecoveryToken> {
        return repository.findAllUnconsumedByUserId(userId)
    }
}
