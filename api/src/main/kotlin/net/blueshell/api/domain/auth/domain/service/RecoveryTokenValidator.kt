package net.blueshell.api.domain.auth.domain.service

import net.blueshell.api.domain.auth.application.exception.*
import net.blueshell.api.domain.auth.domain.model.RecoveryTokenValidation
import net.blueshell.api.domain.auth.persistence.RecoveryToken
import net.blueshell.api.domain.auth.persistence.repository.RecoveryTokenRepository
import net.blueshell.api.shared.enums.ResetType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Domain service responsible for validating recovery tokens.
 * Contains business rules for token validation.
 */
@Component
class RecoveryTokenValidator(
    private val repository: RecoveryTokenRepository,
    private val encoder: PasswordEncoder
) {

    /**
     * Verify a raw token string and return the validated token entity.
     *
     * @param rawToken The raw token string in format "selector.verifier"
     * @param expectedType The expected token type
     * @return The validated RecoveryToken entity
     * @throws MalformedRecoveryTokenException if token format is invalid
     * @throws InvalidRecoveryTokenException if token not found or selector doesn't match
     * @throws InvalidTokenTypeException if token type doesn't match expected
     * @throws ExpiredRecoveryTokenException if token has expired
     * @throws ConsumedRecoveryTokenException if token was already consumed
     * @throws TokenVerificationFailedException if verifier doesn't match
     */
    @Transactional(readOnly = true)
    fun verify(rawToken: String, expectedType: ResetType): RecoveryToken {
        // Parse and validate token format
        val validation = try {
            RecoveryTokenValidation.fromRawToken(rawToken, expectedType)
        } catch (e: IllegalArgumentException) {
            throw MalformedRecoveryTokenException(e.message ?: "Invalid token format")
        }

        // Find token by selector
        val token = repository.findBySelector(validation.selector)
            .orElseThrow { InvalidRecoveryTokenException("Recovery token not found") }

        // Validate token type
        if (token.type != expectedType) {
            throw InvalidTokenTypeException("Token type ${token.type} does not match expected type $expectedType")
        }

        // Check if token is expired
        if (token.isExpired) {
            throw ExpiredRecoveryTokenException("Recovery token has expired")
        }

        // Check if token was already consumed
        if (token.isConsumed) {
            throw ConsumedRecoveryTokenException("Recovery token has already been used")
        }

        // Verify the verifier matches
        if (!encoder.matches(validation.verifier, token.verifierHash)) {
            throw TokenVerificationFailedException("Recovery token verification failed")
        }

        return token
    }

    /**
     * Find all unconsumed tokens for a user.
     */
    @Transactional(readOnly = true)
    fun findUnconsumedByUserId(userId: Long): List<RecoveryToken> {
        return repository.findAllUnconsumedByUserId(userId)
    }
}
