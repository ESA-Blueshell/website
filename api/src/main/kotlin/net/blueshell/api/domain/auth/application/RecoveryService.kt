package net.blueshell.api.domain.auth.application

import net.blueshell.api.domain.auth.persistence.RecoveryToken
import net.blueshell.api.domain.auth.persistence.repository.RecoveryTokenRepository
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.application.event.UserCreated
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.platform.integration.queue.EmailJobs
import net.blueshell.api.platform.integration.queue.JobDispatcher
import net.blueshell.api.shared.enums.ResetType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.context.event.EventListener
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.function.Predicate
import java.util.function.Supplier

@Service
class RecoveryService(
    repository: RecoveryTokenRepository,
    private val encoder: PasswordEncoder,
    private val jobDispatcher: JobDispatcher,
    private val users: UserService
) : BaseModelService<RecoveryToken, Long, RecoveryTokenRepository>(repository) {
    private val random = SecureRandom()

    /**
     * React to user creation: issue appropriate token and send mail.
     */
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @jakarta.transaction.Transactional
    fun onUserCreated(event: UserCreated) {
        val rawToken: String
        val user = users.findById(event.userId)
        if (hasAuthority(Role.BOARD)) {
            rawToken = issue(user, ResetType.MEMBER_ACTIVATION, Duration.ofDays(7))
            jobDispatcher.enqueue(
                EmailJobs.Recovery,
                EmailJobs.RecoveryPayload(user.id!!, rawToken, ResetType.MEMBER_ACTIVATION)
            )
        } else {
            rawToken = issue(user, ResetType.USER_ACTIVATION, Duration.ofHours(1))
            jobDispatcher.enqueue(
                EmailJobs.Recovery,
                EmailJobs.RecoveryPayload(user.id!!, rawToken, ResetType.USER_ACTIVATION)
            )
        }
    }

    /**
     * Always 204 to avoid user enumeration.
     */
    @jakarta.transaction.Transactional
    fun resetPassword(username: String) {
        try {
            val user = users.findByUsername(username)
            val rawToken = issue(user, ResetType.PASSWORD_RESET, Duration.ofMinutes(30))
            jobDispatcher.enqueue(
                EmailJobs.Recovery,
                EmailJobs.RecoveryPayload(user.id!!, rawToken, ResetType.PASSWORD_RESET)
            )
        } catch (notFound: ResponseStatusException) {
            // swallow 404
        }
    }

    @jakarta.transaction.Transactional
    fun setPassword(rawToken: String, newPassword: String) {
        val token = verify(rawToken, ResetType.PASSWORD_RESET)
        users.updatePassword(token.user.id!!, newPassword)
        consume(token)
    }

    @jakarta.transaction.Transactional
    fun activateUser(rawToken: String): User {
        val token = verify(rawToken, ResetType.USER_ACTIVATION)
        users.activateUser(token.user.id!!)
        consume(token)
        return token.user
    }

    @jakarta.transaction.Transactional
    fun activateMember(rawToken: String, username: String, password: String) {
        val token = verify(rawToken, ResetType.MEMBER_ACTIVATION)
        users.setUsernameAndPassword(token.user.id!!, username, password)
        users.activateUser(token.user.id!!)
        consume(token)
    }

    @jakarta.transaction.Transactional
    fun issue(user: User, type: ResetType, ttl: Duration): String {
        // Invalidate all existing active token of this type
        repository.findAllUnconsumedByTypeAndUserId(user.id!!, type)
            .forEach { this.delete(it) }

        val selector = randomUrlSafe(16) // 128-bit
        val verifier = randomUrlSafe(32) // 256-bit

        val token = RecoveryToken()
        token.user = user
        token.type = type
        token.selector = selector
        token.verifierHash = encoder.encode(verifier)
        token.expiresAt = Instant.now().plus(ttl)

        create(token)
        return "$selector.$verifier"
    }

    @jakarta.transaction.Transactional
    fun verify(rawToken: String, expectedType: ResetType): RecoveryToken {
        val parts: Array<String> = rawToken.split("\\.".toRegex(), limit = 2).toTypedArray()
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw notFound()
        }
        val selector = parts[0]
        val verifier = parts[1]

        val token = repository.findBySelector(selector)
            .filter(Predicate { t: RecoveryToken -> t.type == expectedType })
            .orElseThrow<ResponseStatusException>(Supplier { this.notFound() })

        if (token.isConsumed || token.isExpired) {
            throw notFound()
        }
        if (!encoder.matches(verifier, token.verifierHash)) {
            throw notFound()
        }
        return token
    }

    @jakarta.transaction.Transactional
    fun consume(token: RecoveryToken) {
        token.consumedAt = Instant.now()
        update(token)
    }

    private fun notFound(): ResponseStatusException {
        return ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid or expired recovery token")
    }

    private fun randomUrlSafe(numBytes: Int): String {
        val bytes = ByteArray(numBytes)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    // in RecoveryService
    @jakarta.transaction.Transactional
    fun resendActivation(username: String) {
        try {
            val user = users.findByUsername(username)
            if (user.enabled) return
            val rawToken = issue(user, ResetType.USER_ACTIVATION, Duration.ofHours(1))
            jobDispatcher.enqueue(
                EmailJobs.Recovery,
                EmailJobs.RecoveryPayload(user.id!!, rawToken, ResetType.USER_ACTIVATION)
            )
        } catch (ignored: ResponseStatusException) {
            // swallow 404 to avoid enumeration
        }
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @jakarta.transaction.Transactional
    fun resendActivationEmail(userId: Long) {
        val user = users.findById(userId)
        if (user.enabled) return

        val recoveryTokens = repository.findAllUnconsumedByUserId(userId)
        if (recoveryTokens.any { it.type == ResetType.MEMBER_ACTIVATION }) {
            val rawToken = issue(user, ResetType.MEMBER_ACTIVATION, Duration.ofDays(7))
            jobDispatcher.enqueue(
                EmailJobs.Recovery,
                EmailJobs.RecoveryPayload(user.id!!, rawToken, ResetType.MEMBER_ACTIVATION)
            )
        } else if (recoveryTokens.any { it.type == ResetType.USER_ACTIVATION }) {
            val rawToken = issue(user, ResetType.USER_ACTIVATION, Duration.ofHours(1))
            jobDispatcher.enqueue(
                EmailJobs.Recovery,
                EmailJobs.RecoveryPayload(user.id!!, rawToken, ResetType.MEMBER_ACTIVATION)
            )
        }
    }
}
