package net.blueshell.api.auth.domain

import net.blueshell.api.auth.domain.twofactor.TrustedBrowsers
import net.blueshell.api.auth.domain.twofactor.TwoFactor
import net.blueshell.api.auth.persistence.SecurityEventKind
import net.blueshell.api.security.Browser
import net.blueshell.api.security.SignInEndReason
import net.blueshell.api.security.SignInEndedAsSuspicious
import net.blueshell.api.security.SignIns
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.user.api.UserDeleted
import net.blueshell.api.user.api.UserEmailChangedByBoard
import net.blueshell.api.user.api.UserRolesChanged
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import java.time.Instant

class AccountSecurityListenerTest {
    private val events = mock<SecurityEvents>()
    private val twoFactor = mock<TwoFactor>()
    private val trustedBrowsers = mock<TrustedBrowsers>()
    private val signIns = mock<SignIns>()
    private val listener = AccountSecurityListener(events, twoFactor, trustedBrowsers, signIns)
    private val firefox = Browser("Firefox", "Linux")

    @Test
    fun `a sign-in ended as stolen is logged and told, by the system`() {
        listener.onSuspiciousSignIn(SignInEndedAsSuspicious(7, SignInEndReason.REUSED, firefox, Instant.EPOCH))
        listener.onSuspiciousSignIn(SignInEndedAsSuspicious(7, SignInEndReason.BROWSER_CHANGED, firefox, Instant.EPOCH))

        verify(events).record(
            eq(7L),
            eq(SecurityEventKind.SIGN_IN_REUSED),
            eq(SecurityActor.System),
            anyOrNull(),
            eq(firefox),
            anyOrNull(),
        )
        verify(events).record(
            eq(7L),
            eq(SecurityEventKind.SIGN_IN_BROWSER_CHANGED),
            eq(SecurityActor.System),
            anyOrNull(),
            eq(firefox),
            anyOrNull(),
        )
    }

    @Test
    fun `erasure takes the second factor and every sign-in with it`() {
        listener.onUserDeleted(UserDeleted(7))

        verify(twoFactor).erase(7)
        verify(signIns).endAll(7)
    }

    @Test
    fun `a board member's move of an address forgets trusted browsers and tells the old address`() {
        listener.onEmailChangedByBoard(UserEmailChangedByBoard(7, "old@example.com", Actor.user(2, Role.BOARD)))
        listener.onEmailChangedByBoard(UserEmailChangedByBoard(8, "old@example.com"))

        verify(trustedBrowsers).forgetAll(7)
        verify(events).record(
            eq(7L),
            eq(SecurityEventKind.EMAIL_CHANGED_BY_BOARD),
            eq(SecurityActor.Person(2)),
            anyOrNull(),
            anyOrNull(),
            eq("old@example.com"),
        )
        verify(events).record(
            eq(8L),
            eq(SecurityEventKind.EMAIL_CHANGED_BY_BOARD),
            eq(SecurityActor.System),
            anyOrNull(),
            anyOrNull(),
            eq("old@example.com"),
        )
    }

    @Test
    fun `a role grant is logged with the admin who made it`() {
        listener.onRolesChanged(UserRolesChanged(7, Actor.user(1, Role.ADMIN)))
        listener.onRolesChanged(UserRolesChanged(8))

        verify(events).record(
            eq(7L),
            eq(SecurityEventKind.ROLES_CHANGED),
            eq(SecurityActor.Person(1)),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
        )
        verify(events).record(eq(8L), eq(SecurityEventKind.ROLES_CHANGED), eq(SecurityActor.System), anyOrNull(), anyOrNull(), anyOrNull())
        verifyNoInteractions(signIns)
    }

    @Test
    fun `a role granted to somebody without two-factor ends their sign-ins, logged as the admin's`() {
        listener.onRolesChanged(UserRolesChanged(7, Actor.user(1, Role.ADMIN), dormantGranted = setOf(Role.BOARD)))

        verify(signIns).endAll(7)
        verify(events).record(
            eq(7L),
            eq(SecurityEventKind.SIGNED_OUT_EVERYWHERE),
            eq(SecurityActor.Person(1)),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
        )
    }

    @Test
    fun `a member's own change of bank details is logged with the new account, masked`() {
        listener.onBankDetailsChanged(
            net.blueshell.api.user.api
                .BankDetailsChanged(
                    7,
                    net.blueshell.api.user.api
                        .MaskedIban("NL", "34"),
                ),
        )

        verify(events).record(
            eq(7L),
            eq(SecurityEventKind.BANK_DETAILS_CHANGED),
            eq(SecurityActor.Person(7)),
            eq("NL•• … ••34"),
            anyOrNull(),
            anyOrNull(),
        )
    }

    @Test
    fun `a reveal is logged on the member, naming who revealed which membership and never the IBAN`() {
        listener.onIbanRevealed(
            net.blueshell.api.user.api
                .IbanRevealed(userId = 7, membershipId = 12, revealedBy = 3),
        )

        verify(events).record(
            eq(7L),
            eq(SecurityEventKind.IBAN_REVEALED),
            eq(SecurityActor.Person(3)),
            eq("membership 12"),
            anyOrNull(),
            anyOrNull(),
        )
    }

    @Test
    fun `a download of a mandate's PDF is logged on the member, naming who downloaded which membership's`() {
        listener.onMandatePdfDownloaded(
            net.blueshell.api.contribution.api
                .MandatePdfDownloaded(userId = 7, membershipId = 12, downloadedBy = 3),
        )

        verify(events).record(
            eq(7L),
            eq(SecurityEventKind.MANDATE_PDF_DOWNLOADED),
            eq(SecurityActor.Person(3)),
            eq("membership 12"),
            anyOrNull(),
            anyOrNull(),
        )
    }

    @Test
    fun `a download of ING's file is logged on who downloaded it, with the run, the file and how many members it holds`() {
        val downloaded =
            net.blueshell.api.contribution.api.IncassoFileDownloaded(
                downloadedBy = 3,
                runId = 11,
                part = 2,
                parts = 2,
                members = 23,
            )
        listener.onIncassoFileDownloaded(downloaded)
        listener.onIncassoFileDownloaded(downloaded.copy(part = 1, parts = 1, members = 1))

        for (note in listOf("incasso run 11, file 2 of 2, 23 members", "incasso run 11, file 1 of 1, 1 member")) {
            verify(events).record(
                eq(3L),
                eq(SecurityEventKind.INCASSO_FILE_DOWNLOADED),
                eq(SecurityActor.Person(3)),
                eq(note),
                anyOrNull(),
                anyOrNull(),
            )
        }
    }
}
