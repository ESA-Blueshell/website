package net.blueshell.api.domain.auth.application.service

import net.blueshell.api.domain.auth.application.factory.RecoveryTokenFactory
import net.blueshell.api.domain.auth.domain.service.RecoveryTokenValidator
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.application.exception.UserNotFoundException
import net.blueshell.api.shared.enums.ResetType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration

@Service
class PasswordRecoveryService(
    private val users: UserService,
    private val tokenFactory: RecoveryTokenFactory,
    private val tokenValidator: RecoveryTokenValidator
) {

    /**
     * Always returns null for unknown users to avoid user enumeration.
     */
    @Transactional
    fun requestPasswordReset(username: String): RecoveryDispatch? {
        return try {
            val user = users.findByUsername(username)
            val rawToken = tokenFactory.issue(user, ResetType.PASSWORD_RESET, Duration.ofHours(24))
            RecoveryDispatch(user.id!!, rawToken, ResetType.PASSWORD_RESET)
        } catch (notFound: UserNotFoundException) {
            null
        }
    }

    @Transactional
    fun setPassword(rawToken: String, newPassword: String) {
        val token = tokenValidator.verify(rawToken, ResetType.PASSWORD_RESET)
        users.updatePassword(token.user.id!!, newPassword)
        tokenFactory.consume(token)
    }
}
