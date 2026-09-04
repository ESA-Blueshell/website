package net.blueshell.api.auth.domain

import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.api.UserNotFoundException
import net.blueshell.api.user.persistence.User
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
            val rawToken = tokenFactory.issue(user, TokenPurpose.USER_ACTIVATION, ttlFor(TokenPurpose.USER_ACTIVATION))
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
            val rawToken = tokenFactory.issue(user, TokenPurpose.MEMBER_ACTIVATION, ttlFor(TokenPurpose.MEMBER_ACTIVATION))
            RecoveryDispatch(user.id!!, rawToken, TokenPurpose.MEMBER_ACTIVATION)
        } else if (recoveryTokens.any { it.type == TokenPurpose.USER_ACTIVATION }) {
            val rawToken = tokenFactory.issue(user, TokenPurpose.USER_ACTIVATION, ttlFor(TokenPurpose.USER_ACTIVATION))
            RecoveryDispatch(user.id!!, rawToken, TokenPurpose.USER_ACTIVATION)
        } else {
            null
        }
    }

    /**
     * Which activation each account that has not been activated takes.
     *
     * A board-created account activates through the member email, a self-signup through the user
     * email, and an unconsumed token of that kind is what records which — whether or not the
     * link still works, so an expired one still reports what it needs. No token at all reads as
     * a self-signup.
     */
    @Transactional(readOnly = true)
    fun pendingActivations(): Map<Long, TokenPurpose> {
        val memberActivations = tokenValidator.findUserIdsWithUnconsumedType(TokenPurpose.MEMBER_ACTIVATION)
        return users.findAllDisabledIds().associateWith { userId ->
            if (userId in memberActivations) TokenPurpose.MEMBER_ACTIVATION else TokenPurpose.USER_ACTIVATION
        }
    }

    /**
     * Issues an activation link of a chosen kind, whether or not one is outstanding: this is the
     * path where the sender names the email rather than letting `requestActivationEmail` infer
     * it, which does nothing at all once a board-created account's link has expired. Null when
     * the account is already active.
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
        return RecoveryDispatch(user.id!!, tokenFactory.issue(user, purpose, ttlFor(purpose)), purpose)
    }

    /**
     * Used by listeners when a user is created to issue activation token.
     */
    @Transactional
    fun issueActivationForNewUser(userId: Long, createdByBoard: Boolean): RecoveryDispatch {
        val user = users.findById(userId)
        val type = if (createdByBoard) TokenPurpose.MEMBER_ACTIVATION else TokenPurpose.USER_ACTIVATION
        val rawToken = tokenFactory.issue(user, type, ttlFor(type))
        return RecoveryDispatch(user.id!!, rawToken, type)
    }

    companion object {
        /**
         * How long a confirmation link works. Long enough for an applicant who reads their mail
         * that evening: this only enables an account whose password its owner already chose, so
         * it is not a credential. The board's link stays longer still, since it also sets the
         * username and password and arrives unexpected.
         */
        val USER_ACTIVATION_TTL: Duration = Duration.ofHours(24)
        val MEMBER_ACTIVATION_TTL: Duration = Duration.ofDays(7)

        fun ttlFor(purpose: TokenPurpose): Duration =
            if (purpose == TokenPurpose.MEMBER_ACTIVATION) MEMBER_ACTIVATION_TTL else USER_ACTIVATION_TTL
    }
}
