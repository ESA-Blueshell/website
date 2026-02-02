package net.blueshell.api.service

import lombok.extern.slf4j.Slf4j
import net.blueshell.api.base.BaseModelService
import net.blueshell.api.common.enums.ResetType
import net.blueshell.api.common.enums.Role
import net.blueshell.api.common.event.job.RecoveryEmailEvent
import net.blueshell.api.common.event.jpa.PostPersistEvent
import net.blueshell.api.model.RecoveryToken
import net.blueshell.api.model.User
import net.blueshell.api.repository.RecoveryTokenRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.web.server.ResponseStatusException
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.function.Consumer
import java.util.function.Predicate
import java.util.function.Supplier

@Slf4j
@Service
class RecoveryService protected constructor(
    repository: RecoveryTokenRepository,
    private val encoder: PasswordEncoder,
    private val eventPublisher: ApplicationEventPublisher,
    private val users: UserService
) : BaseModelService<RecoveryToken?, RecoveryTokenRepository?>(repository) {
    private val random = SecureRandom()

    /**
     * React to user creation: issue appropriate token and send mail.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @jakarta.transaction.Transactional
    fun onUserCreated(event: PostPersistEvent<User>) {
        val rawToken: String?
        val user = event.getSource()
        if (hasAuthority(Role.BOARD)) {
            rawToken = issue(user, ResetType.MEMBER_ACTIVATION, Duration.ofDays(7))
            eventPublisher.publishEvent(RecoveryEmailEvent(user.getId(), rawToken, ResetType.MEMBER_ACTIVATION))
        } else {
            rawToken = issue(user, ResetType.USER_ACTIVATION, Duration.ofHours(1))
            eventPublisher.publishEvent(RecoveryEmailEvent(user.getId(), rawToken, ResetType.USER_ACTIVATION))
        }
    }

    /**
     * Always 204 to avoid user enumeration.
     */
    @jakarta.transaction.Transactional
    fun resetPassword(username: String?) {
        try {
            val user = users.findByUsername(username)
            val rawToken = issue(user, ResetType.PASSWORD_RESET, Duration.ofMinutes(30))
            eventPublisher.publishEvent(RecoveryEmailEvent(user.getId(), rawToken, ResetType.PASSWORD_RESET))
        } catch (notFound: ResponseStatusException) {
            // swallow 404
        }
    }

    @jakarta.transaction.Transactional
    fun setPassword(rawToken: String?, newPassword: String?) {
        val token = verify(rawToken, ResetType.PASSWORD_RESET)
        users.updatePassword(token.getUser().getId(), newPassword)
        consume(token)
    }

    @jakarta.transaction.Transactional
    fun activateUser(rawToken: String?): User? {
        val token = verify(rawToken, ResetType.USER_ACTIVATION)
        users.activateUser(token.getUser().getId())
        consume(token)
        return token.getUser()
    }

    @jakarta.transaction.Transactional
    fun activateMember(rawToken: String?, username: String?, password: String?) {
        val token = verify(rawToken, ResetType.MEMBER_ACTIVATION)
        users.setUsernameAndPassword(token.getUser().getId(), username, password)
        users.activateUser(token.getUser().getId())
        consume(token)
    }

    @jakarta.transaction.Transactional
    fun issue(user: User, type: ResetType?, ttl: Duration): String {
        // Invalidate all existing active token of this type
        repository!!.findAllByUser_IdAndTypeAndConsumedAtIsNull(user.getId(), type)
            .forEach(Consumer { entity: RecoveryToken? -> this.delete(entity) })

        val selector = randomUrlSafe(16) // 128-bit
        val verifier = randomUrlSafe(32) // 256-bit

        val token = RecoveryToken()
        token.setUser(user)
        token.setType(type)
        token.setSelector(selector)
        token.setVerifierHash(encoder.encode(verifier))
        token.setExpiresAt(Instant.now().plus(ttl))

        create(token)
        return selector + "." + verifier
    }

    @jakarta.transaction.Transactional
    fun verify(rawToken: String?, expectedType: ResetType?): RecoveryToken {
        val parts: Array<String> =
            if (rawToken != null) rawToken.split("\\.".toRegex(), limit = 2).toTypedArray() else arrayOfNulls<String>(0)
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw notFound()
        }
        val selector = parts[0]
        val verifier = parts[1]

        val token = repository!!.findBySelector(selector)
            .filter(Predicate { t: RecoveryToken? -> t!!.getType() == expectedType })
            .orElseThrow<ResponseStatusException?>(Supplier { this.notFound() })

        if (token.isConsumed() || token.isExpired()) {
            throw notFound()
        }
        if (!encoder.matches(verifier, token.getVerifierHash())) {
            throw notFound()
        }
        return token
    }

    @jakarta.transaction.Transactional
    fun consume(token: RecoveryToken) {
        token.setConsumedAt(Instant.now())
        update(token)
    }

    private fun notFound(): ResponseStatusException {
        return ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid or expired recovery token")
    }

    private fun randomUrlSafe(numBytes: Int): String? {
        val bytes = ByteArray(numBytes)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    // in RecoveryService
    @jakarta.transaction.Transactional
    fun resendActivation(username: String?) {
        try {
            val user = users.findByUsername(username)
            if (user.isEnabled()) return
            val rawToken = issue(user, ResetType.USER_ACTIVATION, Duration.ofHours(1))
            eventPublisher.publishEvent(RecoveryEmailEvent(user.getId(), rawToken, ResetType.USER_ACTIVATION))
        } catch (ignored: ResponseStatusException) {
            // swallow 404 to avoid enumeration
        }
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @jakarta.transaction.Transactional
    fun resendActivationEmail(userId: Long?) {
        val user = users.findById(userId)
        if (user.isEnabled()) return

        val recoveryTokens = repository!!.findAllByUser_IdAndConsumedAtIsNull(userId)
        if (recoveryTokens.stream().anyMatch { r: RecoveryToken? -> r!!.getType() == ResetType.MEMBER_ACTIVATION }) {
            val rawToken = issue(user, ResetType.MEMBER_ACTIVATION, Duration.ofDays(7))
            eventPublisher.publishEvent(
                RecoveryEmailEvent(user.getId(), rawToken, ResetType.MEMBER_ACTIVATION)
            )
        } else if (recoveryTokens.stream()
                .anyMatch { r: RecoveryToken? -> r!!.getType() == ResetType.USER_ACTIVATION }
        ) {
            val rawToken = issue(user, ResetType.USER_ACTIVATION, Duration.ofHours(1))
            eventPublisher.publishEvent(RecoveryEmailEvent(user.getId(), rawToken, ResetType.MEMBER_ACTIVATION))
        }
    }
}
