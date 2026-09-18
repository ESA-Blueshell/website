package net.blueshell.api.auth.domain

import net.blueshell.api.shared.enums.TokenPurpose
import net.blueshell.api.user.api.UserNotFoundException
import net.blueshell.api.user.api.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration

@Service
class PasswordRecoveryService(
    private val users: UserService,
    private val tokenFactory: RecoveryTokenFactory,
    private val tokenValidator: RecoveryTokenValidator,
) {
    /**
     * Always returns null for unknown users to avoid user enumeration.
     */
    @Transactional
    fun requestPasswordReset(username: String): RecoveryDispatch? =
        try {
            val user = users.findByUsername(username)
            val rawToken = tokenFactory.issue(user, TokenPurpose.PASSWORD_RESET, Duration.ofHours(24))
            RecoveryDispatch(user.id!!, rawToken, TokenPurpose.PASSWORD_RESET)
        } catch (_: UserNotFoundException) {
            // An unknown username gets the same silence as a known one.
            null
        }

    @Transactional
    fun setPassword(
        rawToken: String,
        newPassword: String,
    ) {
        val token = tokenValidator.verify(rawToken, TokenPurpose.PASSWORD_RESET)
        users.updatePassword(token.user.id!!, newPassword)
        tokenFactory.consume(token)
    }
}
