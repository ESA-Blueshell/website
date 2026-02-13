package net.blueshell.api.domain.auth.application.exception

/**
 * Base exception for all recovery token related failures.
 */
sealed class RecoveryTokenException(message: String) : RuntimeException(message)

/**
 * Thrown when a recovery token has expired.
 */
class ExpiredRecoveryTokenException(message: String = "Recovery token has expired") :
    RecoveryTokenException(message)

/**
 * Thrown when a recovery token has already been consumed.
 */
class ConsumedRecoveryTokenException(message: String = "Recovery token has already been used") :
    RecoveryTokenException(message)

/**
 * Thrown when a recovery token format is invalid.
 */
class MalformedRecoveryTokenException(message: String = "Recovery token format is invalid") :
    RecoveryTokenException(message)

/**
 * Thrown when the token type doesn't match the expected type.
 */
class InvalidTokenTypeException(message: String = "Recovery token type does not match expected type") :
    RecoveryTokenException(message)

/**
 * Thrown when the token verifier doesn't match.
 */
class TokenVerificationFailedException(message: String = "Recovery token verification failed") :
    RecoveryTokenException(message)
