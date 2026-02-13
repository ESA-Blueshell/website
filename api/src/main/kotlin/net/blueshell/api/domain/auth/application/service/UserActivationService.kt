package net.blueshell.api.domain.auth.application.service

import net.blueshell.api.domain.auth.application.factory.RecoveryTokenFactory
import net.blueshell.api.domain.auth.domain.service.RecoveryTokenValidator
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.application.exception.UserNotFoundException
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.enums.ResetType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration

@Service
class UserActivationService(
    private val users: UserService,
    private val tokenFactory: RecoveryTokenFactory,
    private val tokenValidator: RecoveryTokenValidator
) {

    @Transactional
    fun activateUser(rawToken: String): User {
        val token = tokenValidator.verify(rawToken, ResetType.USER_ACTIVATION)
        users.activateUser(token.user.id!!)
        tokenFactory.consume(token)
        return token.user
    }

    @Transactional
    fun activateMember(rawToken: String, username: String, password: String) {
        val token = tokenValidator.verify(rawToken, ResetType.MEMBER_ACTIVATION)
        users.setUsernameAndPassword(token.user.id!!, username, password)
        users.activateUser(token.user.id!!)
        tokenFactory.consume(token)
    }

    /**
     * Resend user activation link by username. Returns null if user not found or already enabled.
     */
    @Transactional
    fun requestUserActivation(username: String): RecoveryDispatch? {
        return try {
            val user = users.findByUsername(username)
            if (user.enabled) return null
            val rawToken = tokenFactory.issue(user, ResetType.USER_ACTIVATION, Duration.ofHours(1))
            RecoveryDispatch(user.id!!, rawToken, ResetType.USER_ACTIVATION)
        } catch (ignored: UserNotFoundException) {
            null
        }
    }

    /**
     * Resend activation email for a specific user id.
     * Chooses member activation token if any unconsumed member token exists, otherwise user activation token.
     */
    @Transactional
    fun requestActivationEmail(userId: Long): RecoveryDispatch? {
        val user = users.findById(userId)
        if (user.enabled) return null

        val recoveryTokens = tokenValidator.findUnconsumedByUserId(userId)
        return if (recoveryTokens.any { it.type == ResetType.MEMBER_ACTIVATION }) {
            val rawToken = tokenFactory.issue(user, ResetType.MEMBER_ACTIVATION, Duration.ofDays(7))
            RecoveryDispatch(user.id!!, rawToken, ResetType.MEMBER_ACTIVATION)
        } else if (recoveryTokens.any { it.type == ResetType.USER_ACTIVATION }) {
            val rawToken = tokenFactory.issue(user, ResetType.USER_ACTIVATION, Duration.ofHours(1))
            RecoveryDispatch(user.id!!, rawToken, ResetType.USER_ACTIVATION)
        } else {
            null
        }
    }

    /**
     * Used by listeners when a user is created to issue activation token.
     */
    @Transactional
    fun issueActivationForNewUser(userId: Long, createdByBoard: Boolean): RecoveryDispatch {
        val user = users.findById(userId)
        val type = if (createdByBoard) ResetType.MEMBER_ACTIVATION else ResetType.USER_ACTIVATION
        val ttl = if (createdByBoard) Duration.ofDays(7) else Duration.ofHours(1)
        val rawToken = tokenFactory.issue(user, type, ttl)
        return RecoveryDispatch(user.id!!, rawToken, type)
    }
}
