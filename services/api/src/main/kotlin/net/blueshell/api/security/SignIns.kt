package net.blueshell.api.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Starts, reads, rotates and ends sign-ins (api ADR-030).
 *
 * A request's token is honoured only while the record agrees with it: the record exists, carries
 * the person's current security stamp, is inside its thirty days and fourteen idle days, began in
 * the same browser, and names the token id as current or as previous within its grace.
 */
@Component
class SignIns(
    private val store: SignInStore,
    private val tokens: JwtTokenUtil,
    private val clock: Clock,
    private val events: ApplicationEventPublisher,
    @param:Value($$"${app.sign-in.lifetime:30d}") private val lifetime: Duration,
    @param:Value($$"${app.sign-in.idle:14d}") private val idle: Duration,
    @param:Value($$"${app.sign-in.rotate-after:5m}") private val rotateAfter: Duration,
    @param:Value($$"${app.sign-in.grace:60s}") private val grace: Duration,
) {
    /** A sign-in and the token that carries it, with how long the cookie may live. */
    data class Issued(
        val signIn: SignIn,
        val token: String,
        val cookieTtl: Duration,
    )

    sealed interface Resolution {
        data class Honoured(
            val signIn: SignIn,
            val rotated: Issued?,
        ) : Resolution

        data object Refused : Resolution
    }

    fun start(
        userId: Long,
        browser: Browser,
        methods: Set<String> = setOf(SignIn.METHOD_PASSWORD),
        steppedUpAt: Instant? = null,
    ): Issued {
        val now = clock.instant()
        val signIn =
            SignIn(
                id = newId(),
                userId = userId,
                startedAt = now,
                lastSeenAt = now,
                browser = browser,
                securityStamp = store.securityStamp(userId),
                currentJti = newId(),
                currentIssuedAt = now,
                steppedUpAt = steppedUpAt,
                methods = methods,
            )
        store.save(signIn, endsAt(signIn))
        return issue(signIn)
    }

    fun resolve(
        token: String?,
        browser: Browser,
        mayRotate: Boolean,
    ): Resolution {
        val claims = tokens.read(token) ?: return Resolution.Refused
        val signIn = store.find(claims.sid) ?: return Resolution.Refused
        val now = clock.instant()

        if (signIn.userId.toString() != claims.subject) return Resolution.Refused
        if (!isCurrent(signIn, now)) {
            store.delete(signIn.id)
            return Resolution.Refused
        }
        if (browser != signIn.browser) return endAsSuspicious(signIn, SignInEndReason.BROWSER_CHANGED, browser, now)

        return when (claims.jti) {
            signIn.currentJti -> Resolution.Honoured(signIn, if (mayRotate) rotateIfDue(signIn, now) else null)
            signIn.previousJti.takeIf { signIn.previousRetiredAt?.plus(grace)?.isAfter(now) == true } ->
                Resolution.Honoured(signIn, null)
            else -> endAsSuspicious(signIn, SignInEndReason.REUSED, browser, now)
        }
    }

    fun find(id: String): SignIn? = store.find(id)

    fun isLive(id: String): Boolean = store.find(id)?.let { isCurrent(it, clock.instant()) } ?: false

    private fun isCurrent(
        signIn: SignIn,
        now: Instant,
    ): Boolean = signIn.securityStamp == store.securityStamp(signIn.userId) && now.isBefore(endsAt(signIn))

    /** The person's live sign-ins, newest first. */
    fun of(userId: Long): List<SignIn> =
        store
            .idsOf(userId)
            .mapNotNull { id -> store.find(id).also { if (it == null) store.unindex(userId, id) } }
            .filter { isLive(it.id) }
            .sortedByDescending { it.startedAt }

    fun end(id: String) = store.delete(id)

    /**
     * Ends every sign-in the person holds by moving their security stamp. The one named by [keep]
     * takes the new stamp and carries on, for a change the person made from it themselves.
     */
    fun endAll(
        userId: Long,
        keep: String? = null,
    ) {
        val kept = keep?.let { store.find(it) }?.takeIf { it.userId == userId }
        val stamp = store.bumpSecurityStamp(userId)
        store.idsOf(userId).filter { it != kept?.id }.forEach {
            store.delete(it)
            store.unindex(userId, it)
        }
        kept?.let { store.save(it.copy(securityStamp = stamp), endsAt(it)) }
    }

    /** Records a proof given just now inside this sign-in, and the `amr` method it adds, if any. */
    fun recordStepUp(
        id: String,
        method: String? = null,
    ) {
        val signIn = store.find(id) ?: return
        val updated = signIn.copy(steppedUpAt = clock.instant(), methods = signIn.methods + listOfNotNull(method))
        store.save(updated, endsAt(updated))
    }

    /** Whether this sign-in was proved within [window]: what a sensitive change asks of it. */
    fun steppedUpWithin(
        signIn: SignIn,
        window: Duration,
    ): Boolean = signIn.steppedUpAt?.let { !it.plus(window).isBefore(clock.instant()) } == true

    fun endsAt(signIn: SignIn): Instant = minOf(signIn.startedAt.plus(lifetime), signIn.lastSeenAt.plus(idle))

    private fun rotateIfDue(
        signIn: SignIn,
        now: Instant,
    ): Issued? {
        if (Duration.between(signIn.currentIssuedAt, now) < rotateAfter) return null
        val newJti = newId()
        val rotated =
            signIn.copy(
                previousJti = signIn.currentJti,
                previousRetiredAt = now,
                currentJti = newJti,
                currentIssuedAt = now,
                lastSeenAt = now,
            )
        if (!store.rotate(signIn.id, signIn.currentJti, newJti, now, endsAt(rotated))) return null
        return issue(rotated)
    }

    private fun endAsSuspicious(
        signIn: SignIn,
        reason: SignInEndReason,
        browser: Browser,
        now: Instant,
    ): Resolution {
        store.delete(signIn.id)
        events.publishEvent(SignInEndedAsSuspicious(signIn.userId, reason, browser, now))
        return Resolution.Refused
    }

    private fun issue(signIn: SignIn): Issued {
        val expiresAt = signIn.startedAt.plus(lifetime)
        return Issued(
            signIn = signIn,
            token = tokens.mint(signIn.userId.toString(), signIn.id, signIn.currentJti, expiresAt),
            cookieTtl = Duration.between(clock.instant(), expiresAt),
        )
    }

    private fun newId(): String = UUID.randomUUID().toString()
}
