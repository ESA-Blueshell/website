package net.blueshell.api.auth.domain

import net.blueshell.api.auth.persistence.SecurityEventKind
import net.blueshell.api.auth.domain.twofactor.Challenges
import net.blueshell.api.auth.domain.twofactor.Proof
import net.blueshell.api.auth.domain.twofactor.TrustedBrowsers
import net.blueshell.api.auth.domain.twofactor.TwoFactor
import net.blueshell.api.auth.domain.twofactor.TwoFactorStanding
import net.blueshell.api.security.Browser
import net.blueshell.api.security.SignIn
import net.blueshell.api.security.SignIns
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.enums.TokenPurpose
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/** Who is signed in, as the login answer states them. */
data class Signer(
    val userId: Long,
    val username: String,
    /** The roles in force: a dormant role is not among them. */
    val roles: List<Role>,
    val addressId: Long?,
    val twoFactor: TwoFactorStanding,
)

/** What a right password led to. */
sealed interface SignInOutcome {
    /** A sign-in, with the trusted-browser cookie to write when one was used or made. */
    data class SignedIn(
        val signer: Signer,
        val issued: SignIns.Issued,
        val trustedBrowser: TrustedBrowsers.Issued?,
    ) : SignInOutcome

    /** Two-factor is on and this browser is not trusted: the code comes next. */
    data class Challenged(
        val challengeId: String,
    ) : SignInOutcome
}

/**
 * Signing in (`docs/flows/sign-in`): the password, then for an account with two-factor either a
 * trusted browser of its own or a code at the challenge.
 */
@Service
class AuthenticationService(
    private val authenticationManager: AuthenticationManager,
    private val users: UserService,
    private val signIns: SignIns,
    private val twoFactor: TwoFactor,
    private val challenges: Challenges,
    private val trustedBrowsers: TrustedBrowsers,
    private val events: SecurityEvents,
    private val tokens: RecoveryTokenValidator,
    private val tokenFactory: RecoveryTokenFactory,
    private val clock: Clock,
) {
    @Transactional
    fun signIn(
        username: String,
        password: String,
        browser: Browser,
        trustedBrowserCookie: String? = null,
    ): SignInOutcome {
        val user = passwordHolder(username, password)
        if (user.awaitingReenrolment) throw ReenrolmentRequired()
        if (!user.hasTwoFactor) return signedIn(user, browser)
        val trusted = trustedBrowsers.redeem(trustedBrowserCookie, requireNotNull(user.id), browser)
        if (trusted != null) return signedIn(user, browser, trustedBrowser = trusted)
        return SignInOutcome.Challenged(challenges.open(requireNotNull(user.id), browser).id)
    }

    /** The code step. A wrong code costs the challenge a try and the account one of its ten. */
    @Transactional(noRollbackFor = [WrongCode::class])
    fun answerChallenge(
        challengeId: String?,
        code: String,
        browser: Browser,
        trustThisBrowser: Boolean,
    ): SignInOutcome.SignedIn {
        val challenge = challenges.find(challengeId)?.takeIf { it.browser == browser } ?: throw ChallengeExpired()
        if (challenges.isThrottled(challenge.userId)) throw CodeLimitReached()
        if (twoFactor.prove(challenge.userId, code) == null) {
            val triesLeft = challenges.fail(challenge)
            if (challenges.countFailure(challenge.userId)) events.record(challenge.userId, SecurityEventKind.CODE_LIMIT_REACHED)
            throw WrongCode(triesLeft)
        }
        challenges.close(challenge.id)
        val user = users.findById(challenge.userId)
        val trusted =
            if (trustThisBrowser) {
                trustedBrowsers.trust(challenge.userId, browser).also {
                    events.record(challenge.userId, SecurityEventKind.TRUSTED_BROWSER_ADDED)
                }
            } else {
                null
            }
        return signedIn(user, browser, withCode = true, trustedBrowser = trusted)
    }

    /** After a two-factor reset: the password and the emailed link, together, open a sign-in. */
    @Transactional
    fun reenrol(
        username: String,
        password: String,
        rawToken: String,
        browser: Browser,
    ): SignInOutcome.SignedIn {
        val user = passwordHolder(username, password)
        val token = tokens.verify(rawToken, TokenPurpose.TWO_FACTOR_REENROLMENT)
        if (token.user.id != user.id) throw InvalidRecoveryTokenException()
        tokenFactory.consume(token)
        user.awaitingReenrolment = false
        users.update(user)
        events.record(requireNotNull(user.id), SecurityEventKind.REENROLLED)
        return signedIn(user, browser)
    }

    fun signerOf(userId: Long): Signer = signerOf(users.findById(userId))

    private fun passwordHolder(
        username: String,
        password: String,
    ): User {
        authenticationManager.authenticate(UsernamePasswordAuthenticationToken(username, password))
        return users.findByUsername(username)
    }

    private fun signedIn(
        user: User,
        browser: Browser,
        withCode: Boolean = false,
        trustedBrowser: TrustedBrowsers.Issued? = null,
    ): SignInOutcome.SignedIn {
        val userId = requireNotNull(user.id)
        val issued =
            signIns.start(
                userId,
                browser,
                methods = if (withCode) setOf(SignIn.METHOD_PASSWORD, SignIn.METHOD_OTP) else setOf(SignIn.METHOD_PASSWORD),
                steppedUpAt = if (withCode) clock.instant() else null,
            )
        if (events.hasSignedInFrom(userId, null) && !events.hasSignedInFrom(userId, browser.label)) {
            events.record(userId, SecurityEventKind.NEW_BROWSER, browser = browser.label)
        }
        events.record(userId, SecurityEventKind.SIGNED_IN, browser = browser.label)
        return SignInOutcome.SignedIn(signerOf(user), issued, trustedBrowser)
    }

    private fun signerOf(user: User) =
        Signer(
            userId = requireNotNull(user.id),
            username = user.username,
            roles = user.inheritedRolesInForce.sortedBy { it.ordinal },
            addressId = user.addressId,
            twoFactor = twoFactor.standing(requireNotNull(user.id)),
        )
}
