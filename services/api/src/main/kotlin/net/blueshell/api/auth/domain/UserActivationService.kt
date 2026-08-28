package net.blueshell.api.auth.domain

import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.application.exception.UserNotFoundException
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.enums.TokenPurpose
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
        val token = tokenValidator.verify(rawToken, TokenPurpose.USER_ACTIVATION)
        users.activateUser(token.user.id!!)
        tokenFactory.consume(token)
        return token.user
    }

    @Transactional
    fun activateMember(rawToken: String, username: String, password: String) {
        val token = tokenValidator.verify(rawToken, TokenPurpose.MEMBER_ACTIVATION)
        users.setUsernameAndPassword(token.user.id!!, username, password)
        users.activateUser(token.user.id!!)
        tokenFactory.consume(token)
    }

    /**
     * Consume every outstanding confirmation link of one kind for a user, so links already
     * delivered stop working. Used when the address itself changes, and before issuing a
     * replacement.
     */
    @Transactional
    fun revokeOutstandingActivations(userId: Long, purpose: TokenPurpose = TokenPurpose.USER_ACTIVATION) {
        tokenValidator.findUnconsumedByUserId(userId)
            .filter { it.type == purpose }
            .forEach { tokenFactory.consume(it) }
    }

    /**
     * Resend user activation link by username. Returns null if user not found or already enabled.
     *
     * Retires whatever was outstanding first, so the address holds one live link
     * at a time however often the applicant asks for another. Otherwise every
     * press of "resend" leaves another working link behind.
     */
    @Transactional
    fun requestUserActivation(username: String): RecoveryDispatch? {
        return try {
            val user = users.findByUsername(username)
            if (user.enabled) return null
            revokeOutstandingActivations(user.id!!)
            val rawToken = tokenFactory.issue(user, TokenPurpose.USER_ACTIVATION, Duration.ofHours(1))
            RecoveryDispatch(user.id!!, rawToken, TokenPurpose.USER_ACTIVATION)
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
        return if (recoveryTokens.any { it.type == TokenPurpose.MEMBER_ACTIVATION }) {
            val rawToken = tokenFactory.issue(user, TokenPurpose.MEMBER_ACTIVATION, Duration.ofDays(7))
            RecoveryDispatch(user.id!!, rawToken, TokenPurpose.MEMBER_ACTIVATION)
        } else if (recoveryTokens.any { it.type == TokenPurpose.USER_ACTIVATION }) {
            val rawToken = tokenFactory.issue(user, TokenPurpose.USER_ACTIVATION, Duration.ofHours(1))
            RecoveryDispatch(user.id!!, rawToken, TokenPurpose.USER_ACTIVATION)
        } else {
            null
        }
    }

    /**
     * Which activation each account that has not been activated takes.
     *
     * An account created by the board activates through the member email; one that signed
     * itself up activates through the user email. An unconsumed token of a kind is what
     * records which happened, whether or not that link still works — so an account whose
     * link has expired still reports the kind it needs, and stays reachable.
     *
     * Accounts with no token at all read as a self-signup, which is what an account with no
     * board involvement is.
     */
    @Transactional(readOnly = true)
    fun pendingActivations(): Map<Long, TokenPurpose> {
        val memberActivations = tokenValidator.findUserIdsWithUnconsumedType(TokenPurpose.MEMBER_ACTIVATION)
        return users.findAllDisabledIds().associateWith { userId ->
            if (userId in memberActivations) TokenPurpose.MEMBER_ACTIVATION else TokenPurpose.USER_ACTIVATION
        }
    }

    /**
     * Issue an activation link of a chosen kind, whether or not one is already outstanding.
     *
     * `requestActivationEmail` picks the kind from what happens to be outstanding and does
     * nothing when neither is, which leaves an account created by the board unreachable once
     * its link has expired. This is the path for choosing, so the sender says which email
     * they mean rather than discovering it afterwards. Returns null when the account is
     * already active and so has nothing to activate.
     */
    @Transactional
    fun requestActivation(userId: Long, purpose: TokenPurpose): RecoveryDispatch? {
        require(purpose == TokenPurpose.USER_ACTIVATION || purpose == TokenPurpose.MEMBER_ACTIVATION) {
            "$purpose is not an activation"
        }
        val user = users.findById(userId)
        if (user.enabled) return null

        // One live link of a kind at a time, however often a resend is asked for.
        revokeOutstandingActivations(user.id!!, purpose)
        val ttl = if (purpose == TokenPurpose.MEMBER_ACTIVATION) Duration.ofDays(7) else Duration.ofHours(1)
        return RecoveryDispatch(user.id!!, tokenFactory.issue(user, purpose, ttl), purpose)
    }

    /**
     * Used by listeners when a user is created to issue activation token.
     */
    @Transactional
    fun issueActivationForNewUser(userId: Long, createdByBoard: Boolean): RecoveryDispatch {
        val user = users.findById(userId)
        val type = if (createdByBoard) TokenPurpose.MEMBER_ACTIVATION else TokenPurpose.USER_ACTIVATION
        val ttl = if (createdByBoard) Duration.ofDays(7) else Duration.ofHours(1)
        val rawToken = tokenFactory.issue(user, type, ttl)
        return RecoveryDispatch(user.id!!, rawToken, type)
    }
}
