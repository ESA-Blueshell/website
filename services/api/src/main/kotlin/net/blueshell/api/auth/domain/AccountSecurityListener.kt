package net.blueshell.api.auth.domain

import net.blueshell.api.auth.domain.twofactor.TrustedBrowsers
import net.blueshell.api.auth.domain.twofactor.TwoFactor
import net.blueshell.api.auth.persistence.SecurityEventKind
import net.blueshell.api.contribution.api.IncassoFileDownloaded
import net.blueshell.api.security.SignInEndReason
import net.blueshell.api.security.SignInEndedAsSuspicious
import net.blueshell.api.security.SignIns
import net.blueshell.api.shared.event.AfterCommitListener
import net.blueshell.api.user.api.BankDetailsChanged
import net.blueshell.api.user.api.IbanRevealed
import net.blueshell.api.user.api.UserDeleted
import net.blueshell.api.user.api.UserEmailChangedByBoard
import net.blueshell.api.user.api.UserRolesChanged
import org.springframework.stereotype.Component

/**
 * Account security's side of things other modules do: a stolen-looking sign-in, an erasure, a board
 * edit, a role grant. A role granted to somebody without two-factor ends their sign-ins, so the next
 * one runs the set-up (api ADR-031).
 */
@Component
class AccountSecurityListener(
    private val events: SecurityEvents,
    private val twoFactor: TwoFactor,
    private val trustedBrowsers: TrustedBrowsers,
    private val signIns: SignIns,
) {
    @AfterCommitListener
    fun onSuspiciousSignIn(evt: SignInEndedAsSuspicious) {
        val kind =
            when (evt.reason) {
                SignInEndReason.REUSED -> SecurityEventKind.SIGN_IN_REUSED
                SignInEndReason.BROWSER_CHANGED -> SecurityEventKind.SIGN_IN_BROWSER_CHANGED
            }
        events.record(evt.userId, kind, SecurityActor.System, browser = evt.browser)
    }

    /** Erasure takes the second factor with it, so a restored account comes back without one. */
    @AfterCommitListener
    fun onUserDeleted(evt: UserDeleted) {
        twoFactor.erase(evt.userId)
        signIns.endAll(evt.userId)
    }

    @AfterCommitListener
    fun onEmailChangedByBoard(evt: UserEmailChangedByBoard) {
        trustedBrowsers.forgetAll(evt.userId)
        events.record(
            evt.userId,
            SecurityEventKind.EMAIL_CHANGED_BY_BOARD,
            evt.actor.userId?.let { SecurityActor.Person(it) } ?: SecurityActor.System,
            oldAddress = evt.oldEmail,
        )
    }

    @AfterCommitListener
    fun onBankDetailsChanged(evt: BankDetailsChanged) {
        events.record(evt.userId, SecurityEventKind.BANK_DETAILS_CHANGED, note = evt.iban.toString())
    }

    /** Who revealed whose membership, never the IBAN itself. */
    @AfterCommitListener
    fun onIbanRevealed(evt: IbanRevealed) {
        events.record(
            evt.userId,
            SecurityEventKind.IBAN_REVEALED,
            SecurityActor.Person(evt.revealedBy),
            note = "membership ${evt.membershipId}",
        )
    }

    /** On the log of whoever downloaded it: which run, which file of it and how many members it holds. */
    @AfterCommitListener
    fun onIncassoFileDownloaded(evt: IncassoFileDownloaded) {
        val members = if (evt.members == 1) "1 member" else "${evt.members} members"
        events.record(
            evt.downloadedBy,
            SecurityEventKind.INCASSO_FILE_DOWNLOADED,
            note = "incasso run ${evt.runId}, file ${evt.part} of ${evt.parts}, $members",
        )
    }

    @AfterCommitListener
    fun onRolesChanged(evt: UserRolesChanged) {
        val actor = evt.actor.userId?.let { SecurityActor.Person(it) } ?: SecurityActor.System
        events.record(evt.userId, SecurityEventKind.ROLES_CHANGED, actor)
        if (evt.dormantGranted.isEmpty()) return
        signIns.endAll(evt.userId)
        events.record(evt.userId, SecurityEventKind.SIGNED_OUT_EVERYWHERE, actor)
    }
}
