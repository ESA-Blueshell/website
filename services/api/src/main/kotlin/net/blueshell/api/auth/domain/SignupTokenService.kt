package net.blueshell.api.auth.domain

import net.blueshell.api.shared.model.SignupSession
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.enums.TokenPurpose
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

/**
 * Lifecycle of the signup continuation token (ADR-024). Lives in the application
 * layer because controllers may not call services directly, which also keeps the
 * raw token out of the web layer.
 */
@Service
class SignupTokenService(
    private val tokenFactory: RecoveryTokenFactory,
    private val tokenValidator: RecoveryTokenValidator,
    private val users: UserService,
) {

    @Transactional
    fun issue(user: User): SignupSession {
        val rawToken = tokenFactory.issue(user, TokenPurpose.SIGNUP_CONTINUATION, TTL)
        return SignupSession(
            userId = requireNotNull(user.id) { "Cannot issue a signup token for an unsaved user" },
            email = user.email,
            token = rawToken,
            expiresAt = Instant.now().plus(TTL),
        )
    }

    /**
     * Asking only for SIGNUP_CONTINUATION is what stops an activation or reset token
     * opening this door. Loads the account rather than returning the token's lazy
     * association, so a caller can attach new entities to it.
     */
    @Transactional(readOnly = true)
    fun resolveAccount(rawToken: String): SignupAccount {
        val token = tokenValidator.verify(rawToken, TokenPurpose.SIGNUP_CONTINUATION)
        val userId = requireNotNull(token.user.id) { "Signup token has no owner" }
        return SignupAccount(id = userId, user = users.findById(userId))
    }

    /**
     * Called when the membership starts, never on email confirmation — an applicant
     * who reads their email mid-form must be able to carry on (ADR-025). Scoped to
     * the signup purpose so an unclicked activation link survives.
     */
    @Transactional
    fun retire(userId: Long) {
        tokenValidator.findUnconsumedByUserId(userId)
            .filter { it.type == TokenPurpose.SIGNUP_CONTINUATION }
            .forEach { tokenFactory.consume(it) }
    }

    companion object {
        // Long enough for a form with a mail round-trip in it, short enough that a
        // leaked sessionStorage value goes stale quickly.
        val TTL: Duration = Duration.ofHours(2)
    }
}
