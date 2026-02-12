package net.blueshell.api.domain.auth.application

import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.application.event.UserCreated
import net.blueshell.api.domain.user.application.exception.UserNotFoundException
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.platform.integration.queue.EmailJobs
import net.blueshell.api.platform.integration.queue.JobDispatcher
import net.blueshell.api.shared.enums.ResetType
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Duration

@Service
class RecoveryService(
    private val jobDispatcher: JobDispatcher,
    private val users: UserService,
    private val tokenManager: RecoveryTokenManager
) {

    /**
     * React to user creation: issue appropriate token and send mail.
     */
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onUserCreated(event: UserCreated) {
        val user = users.findById(event.userId)
        val isBoard = event.createdByBoard == true
        val type = if (isBoard) ResetType.MEMBER_ACTIVATION else ResetType.USER_ACTIVATION
        val ttl = if (isBoard) Duration.ofDays(7) else Duration.ofHours(1)
        val rawToken = tokenManager.issue(user, type, ttl)
        jobDispatcher.enqueue(
            EmailJobs.Recovery,
            EmailJobs.RecoveryPayload(user.id!!, rawToken, type)
        )
    }

    /**
     * Always 204 to avoid user enumeration.
     */
    @Transactional
    fun resetPassword(username: String) {
        try {
            val user = users.findByUsername(username)
            val rawToken = tokenManager.issue(user, ResetType.PASSWORD_RESET, Duration.ofHours(24))
            jobDispatcher.enqueue(
                EmailJobs.Recovery,
                EmailJobs.RecoveryPayload(user.id!!, rawToken, ResetType.PASSWORD_RESET)
            )
        } catch (notFound: UserNotFoundException) {
            // swallow 404
        }
    }

    @Transactional
    fun setPassword(rawToken: String, newPassword: String) {
        val token = tokenManager.verify(rawToken, ResetType.PASSWORD_RESET)
        users.updatePassword(token.user.id!!, newPassword)
        tokenManager.consume(token)
    }

    @Transactional
    fun activateUser(rawToken: String): User {
        val token = tokenManager.verify(rawToken, ResetType.USER_ACTIVATION)
        users.activateUser(token.user.id!!)
        tokenManager.consume(token)
        return token.user
    }

    @Transactional
    fun activateMember(rawToken: String, username: String, password: String) {
        val token = tokenManager.verify(rawToken, ResetType.MEMBER_ACTIVATION)
        users.setUsernameAndPassword(token.user.id!!, username, password)
        users.activateUser(token.user.id!!)
        tokenManager.consume(token)
    }

    // in RecoveryService
    @Transactional
    fun resendActivation(username: String) {
        try {
            val user = users.findByUsername(username)
            if (user.enabled) return
            val rawToken = tokenManager.issue(user, ResetType.USER_ACTIVATION, Duration.ofHours(1))
            jobDispatcher.enqueue(
                EmailJobs.Recovery,
                EmailJobs.RecoveryPayload(user.id!!, rawToken, ResetType.USER_ACTIVATION)
            )
        } catch (ignored: UserNotFoundException) {
            // swallow 404 to avoid enumeration
        }
    }

    @Transactional
    fun resendActivationEmail(userId: Long) {
        val user = users.findById(userId)
        if (user.enabled) return

        val recoveryTokens = tokenManager.findUnconsumedByUserId(userId)
        if (recoveryTokens.any { it.type == ResetType.MEMBER_ACTIVATION }) {
            val rawToken = tokenManager.issue(user, ResetType.MEMBER_ACTIVATION, Duration.ofDays(7))
            jobDispatcher.enqueue(
                EmailJobs.Recovery,
                EmailJobs.RecoveryPayload(user.id!!, rawToken, ResetType.MEMBER_ACTIVATION)
            )
        } else if (recoveryTokens.any { it.type == ResetType.USER_ACTIVATION }) {
            val rawToken = tokenManager.issue(user, ResetType.USER_ACTIVATION, Duration.ofHours(1))
            jobDispatcher.enqueue(
                EmailJobs.Recovery,
                EmailJobs.RecoveryPayload(user.id!!, rawToken, ResetType.USER_ACTIVATION)
            )
        }
    }
}
