package net.blueshell.api.auth.domain

import net.blueshell.api.auth.persistence.SecurityEvent
import net.blueshell.api.auth.persistence.SecurityEventKind
import net.blueshell.api.auth.persistence.TrustedBrowser
import net.blueshell.api.auth.domain.twofactor.Challenges
import net.blueshell.api.auth.domain.twofactor.TrustedBrowsers
import net.blueshell.api.auth.domain.twofactor.TwoFactor
import net.blueshell.api.security.SignIn
import net.blueshell.api.security.SignIns
import net.blueshell.api.security.StepUp
import net.blueshell.api.shared.enums.TokenPurpose
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration

/** How an admin sees one person's account security in the user manager. */
data class AccountStanding(
    val twoFactorOn: Boolean,
    val awaitingReenrolment: Boolean,
    val locked: Boolean,
)

/**
 * Everything a person changes about how they sign in, and what an admin does for somebody who
 * cannot (`docs/flows/security-page`, `docs/flows/account-lock`, `docs/flows/two-factor`).
 */
@Service
// The account-security surface, one use case per endpoint.
@Suppress("TooManyFunctions", "LongParameterList")
class AccountSecurity(
    private val users: UserService,
    private val passwords: PasswordEncoder,
    private val twoFactor: TwoFactor,
    private val challenges: Challenges,
    private val trustedBrowsers: TrustedBrowsers,
    private val signIns: SignIns,
    private val stepUp: StepUp,
    private val events: SecurityEvents,
    private val tokenFactory: RecoveryTokenFactory,
    private val tokenValidator: RecoveryTokenValidator,
    private val recovery: RecoveryUseCases,
    private val jobs: JobQueue,
    private val clock: Clock,
) {
    /**
     * Proves the person inside their sign-in: a code where two-factor is on, the password where it
     * is not. A wrong code counts against the account's ten, like one at the challenge.
     */
    @Transactional(noRollbackFor = [WrongCode::class, WrongPassword::class])
    // One throw per way a proof fails: the account's limit, a wrong code, a wrong password.
    @Suppress("ThrowsCount")
    fun stepUp(
        userId: Long,
        signInId: String,
        code: String?,
        password: String?,
    ) {
        val user = users.findById(userId)
        if (user.hasTwoFactor) {
            if (challenges.isThrottled(userId)) throw CodeLimitReached()
            if (code.isNullOrBlank() || twoFactor.prove(userId, code) == null) {
                if (challenges.countFailure(userId)) events.record(userId, SecurityEventKind.CODE_LIMIT_REACHED)
                throw WrongCode(null)
            }
            signIns.recordStepUp(signInId, SignIn.METHOD_OTP)
        } else {
            if (password.isNullOrBlank() || !passwords.matches(password, user.password)) throw WrongPassword()
            signIns.recordStepUp(signInId)
        }
    }

    @Transactional
    fun setUpTwoFactor(
        userId: Long,
        password: String,
    ) = users.findById(userId).let {
        if (it.hasTwoFactor) stepUp.require()
        twoFactor.setUp(userId, password)
    }

    @Transactional
    fun turnOffTwoFactor(userId: Long) {
        stepUp.require()
        twoFactor.turnOff(userId)
    }

    @Transactional
    fun regenerateBackupCodes(userId: Long): List<String> {
        stepUp.require()
        return twoFactor.regenerateBackupCodes(userId)
    }

    /** Changes the password from inside a sign-in. The others end; this one carries on. */
    @Transactional
    fun changePassword(
        userId: Long,
        signInId: String,
        currentPassword: String,
        newPassword: String,
    ) {
        val user = users.findById(userId)
        if (user.hasTwoFactor) stepUp.require()
        if (!passwords.matches(currentPassword, user.password)) throw WrongPassword()
        users.updatePassword(userId, newPassword)
        trustedBrowsers.forgetAll(userId)
        signIns.endAll(userId, keep = signInId)
        events.record(userId, SecurityEventKind.PASSWORD_CHANGED)
    }

    /**
     * Asks to move the account to [newEmail]. Nothing moves until the link sent there is followed;
     * the address it leaves is told now, with a lock link, while it can still stop the move.
     */
    @Transactional
    fun requestEmailChange(
        userId: Long,
        newEmail: String,
    ) {
        stepUp.require()
        val address = newEmail.trim().lowercase()
        if (users.existsByEmailAndIdNot(address, userId)) throw EmailTaken()
        val user = users.findById(userId)
        user.pendingEmail = address
        users.update(user)
        val token = tokenFactory.issue(user, TokenPurpose.EMAIL_CHANGE, EMAIL_CHANGE_TTL)
        jobs.runAsync(EmailJobs.Recovery, EmailJobs.RecoveryPayload(userId, token, TokenPurpose.EMAIL_CHANGE))
        events.record(userId, SecurityEventKind.EMAIL_CHANGE_REQUESTED, note = address)
    }

    @Transactional
    fun confirmEmailChange(rawToken: String) {
        val token = tokenValidator.verify(rawToken, TokenPurpose.EMAIL_CHANGE)
        val user = token.user
        val address = user.pendingEmail ?: throw InvalidRecoveryTokenException()
        if (users.existsByEmailAndIdNot(address, requireNotNull(user.id))) throw EmailTaken()
        tokenFactory.consume(token)
        user.email = address
        user.pendingEmail = null
        users.update(user)
        events.record(requireNotNull(user.id), SecurityEventKind.EMAIL_CHANGED, note = address)
    }

    /** Follows a lock link. A used, expired or unknown link does nothing, and says so to nobody. */
    @Transactional
    fun lockWithLink(rawToken: String) {
        val token = tokenValidator.findUsable(rawToken, TokenPurpose.ACCOUNT_LOCK) ?: return
        tokenFactory.consume(token)
        lock(token.user)
    }

    /** Unlocks after an admin has heard from the person, and sends them a way back in. */
    @Transactional
    fun unlock(
        adminId: Long,
        userId: Long,
        reason: String,
        correctedEmail: String?,
    ) {
        val user = users.findById(userId)
        if (user.lockedAt == null) throw NotLocked()
        correctedEmail?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && it != user.email }?.let { address ->
            if (users.existsByEmailAndIdNot(address, userId)) throw EmailTaken()
            user.email = address
        }
        user.lockedAt = null
        users.update(user)
        events.record(userId, SecurityEventKind.ACCOUNT_UNLOCKED, SecurityActor.Person(adminId), note = reason)
        recovery.resetPassword(user.username)
        if (user.hasTwoFactor) resetTwoFactor(userId, SecurityActor.Person(adminId), reason)
    }

    /** An admin clearing somebody else's two-factor after they lost both the app and the codes. */
    @Transactional
    fun resetTwoFactorFor(
        adminId: Long,
        userId: Long,
        reason: String,
    ) {
        if (adminId == userId) throw OwnAccount()
        stepUp.require()
        if (!users.findById(userId).hasTwoFactor) throw TwoFactorOff()
        resetTwoFactor(userId, SecurityActor.Person(adminId), reason)
    }

    @Transactional
    fun resendReenrolmentLink(
        adminId: Long,
        userId: Long,
    ) {
        if (adminId == userId) throw OwnAccount()
        val user = users.findById(userId)
        if (!user.awaitingReenrolment) throw NotAwaitingReenrolment()
        sendReenrolmentLink(user)
    }

    /**
     * The reset itself, for an admin or the operator's break-glass command: secrets, codes and
     * trusted browsers gone, every sign-in ended, and a re-enrolment link sent.
     */
    @Transactional
    fun resetTwoFactor(
        userId: Long,
        actor: SecurityActor,
        reason: String,
    ) {
        twoFactor.erase(userId)
        val user = users.findById(userId)
        user.awaitingReenrolment = true
        users.update(user)
        signIns.endAll(userId)
        sendReenrolmentLink(user)
        events.record(userId, SecurityEventKind.TWO_FACTOR_RESET, actor, note = reason, browser = null)
    }

    /** Locks the account for [actor]: nobody signs in, every sign-in ends, a pending move is dropped. */
    @Transactional
    fun lock(
        user: User,
        actor: SecurityActor = SecurityActor.Person(requireNotNull(user.id)),
    ) {
        val userId = requireNotNull(user.id)
        if (user.lockedAt != null) return
        user.lockedAt = clock.instant()
        user.pendingEmail = null
        users.update(user)
        tokenValidator
            .findUnconsumedByUserId(userId)
            .filter { it.type == TokenPurpose.EMAIL_CHANGE }
            .forEach { tokenFactory.consume(it) }
        trustedBrowsers.forgetAll(userId)
        signIns.endAll(userId)
        events.record(userId, SecurityEventKind.ACCOUNT_LOCKED, actor)
    }

    @Transactional
    fun signOutEverywhere(userId: Long) {
        signIns.endAll(userId)
        events.record(userId, SecurityEventKind.SIGNED_OUT_EVERYWHERE)
    }

    fun endSignIn(
        userId: Long,
        signInId: String,
    ): Boolean {
        val signIn = signIns.find(signInId)?.takeIf { it.userId == userId } ?: return false
        signIns.end(signIn.id)
        return true
    }

    fun signInsOf(userId: Long): List<SignIn> = signIns.of(userId)

    fun trustedBrowsersOf(userId: Long): List<TrustedBrowser> = trustedBrowsers.of(userId)

    fun forgetTrustedBrowser(
        userId: Long,
        id: Long,
    ): Boolean = trustedBrowsers.forget(userId, id)

    fun forgetTrustedBrowsers(userId: Long) = trustedBrowsers.forgetAll(userId)

    fun eventsOf(
        userId: Long,
        pageable: Pageable,
    ): Page<SecurityEvent> = events.of(userId, pageable)

    @Transactional(readOnly = true)
    fun standingOf(userId: Long): AccountStanding =
        users.findById(userId).let { AccountStanding(it.hasTwoFactor, it.awaitingReenrolment, it.lockedAt != null) }

    private fun sendReenrolmentLink(user: User) {
        val token = tokenFactory.issue(user, TokenPurpose.TWO_FACTOR_REENROLMENT, REENROLMENT_TTL)
        jobs.runAsync(
            EmailJobs.Recovery,
            EmailJobs.RecoveryPayload(requireNotNull(user.id), token, TokenPurpose.TWO_FACTOR_REENROLMENT),
        )
    }

    companion object {
        val EMAIL_CHANGE_TTL: Duration = Duration.ofHours(24)
        val REENROLMENT_TTL: Duration = Duration.ofHours(24)
    }
}
