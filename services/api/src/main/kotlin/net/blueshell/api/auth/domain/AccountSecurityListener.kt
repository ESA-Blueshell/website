package net.blueshell.api.auth.domain

import net.blueshell.api.auth.persistence.SecurityEventKind
import net.blueshell.api.auth.domain.twofactor.TrustedBrowsers
import net.blueshell.api.auth.domain.twofactor.TwoFactor
import net.blueshell.api.security.SignInEndReason
import net.blueshell.api.security.SignInEndedAsSuspicious
import net.blueshell.api.security.SignIns
import net.blueshell.api.user.api.UserDeleted
import net.blueshell.api.user.api.UserEmailChangedByBoard
import net.blueshell.api.user.api.UserRolesChanged
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/** Account security's side of things other modules do: a stolen-looking sign-in, an erasure, a board edit, a role grant. */
@Component
class AccountSecurityListener(
    private val events: SecurityEvents,
    private val twoFactor: TwoFactor,
    private val trustedBrowsers: TrustedBrowsers,
    private val signIns: SignIns,
) {
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onSuspiciousSignIn(evt: SignInEndedAsSuspicious) {
        val kind =
            when (evt.reason) {
                SignInEndReason.REUSED -> SecurityEventKind.SIGN_IN_REUSED
                SignInEndReason.BROWSER_CHANGED -> SecurityEventKind.SIGN_IN_BROWSER_CHANGED
            }
        events.record(evt.userId, kind, SecurityActor.System, browser = evt.browser)
    }

    /** Erasure takes the second factor with it, so a restored account comes back without one. */
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onUserDeleted(evt: UserDeleted) {
        twoFactor.erase(evt.userId)
        signIns.endAll(evt.userId)
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onEmailChangedByBoard(evt: UserEmailChangedByBoard) {
        trustedBrowsers.forgetAll(evt.userId)
        events.record(
            evt.userId,
            SecurityEventKind.EMAIL_CHANGED_BY_BOARD,
            evt.actor.userId?.let { SecurityActor.Person(it) } ?: SecurityActor.System,
            oldAddress = evt.oldEmail,
        )
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onRolesChanged(evt: UserRolesChanged) {
        events.record(
            evt.userId,
            SecurityEventKind.ROLES_CHANGED,
            evt.actor.userId?.let { SecurityActor.Person(it) } ?: SecurityActor.System,
        )
    }
}
