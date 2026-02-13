package net.blueshell.api.domain.auth.application

import net.blueshell.api.domain.auth.application.factory.RecoveryTokenFactory
import net.blueshell.api.domain.auth.domain.service.RecoveryTokenValidator
import net.blueshell.api.domain.auth.persistence.RecoveryToken
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.enums.ResetType
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * Facade for recovery token operations.
 * Delegates to factory and validator for better separation of concerns.
 *
 * @deprecated This class is maintained for backward compatibility.
 * New code should use RecoveryTokenFactory and RecoveryTokenValidator directly.
 */
@Service
@Deprecated("Use RecoveryTokenFactory and RecoveryTokenValidator instead")
class RecoveryTokenManager(
    private val factory: RecoveryTokenFactory,
    private val validator: RecoveryTokenValidator
) {

    fun issue(user: User, type: ResetType, ttl: Duration): String {
        return factory.issue(user, type, ttl)
    }

    fun verify(rawToken: String, expectedType: ResetType): RecoveryToken {
        return validator.verify(rawToken, expectedType)
    }

    fun consume(token: RecoveryToken) {
        factory.consume(token)
    }

    fun findUnconsumedByUserId(userId: Long): List<RecoveryToken> {
        return validator.findUnconsumedByUserId(userId)
    }
}
