package net.blueshell.api.auth.web

import net.blueshell.api.auth.domain.AccountSecurity
import net.blueshell.api.auth.domain.AccountStanding
import net.blueshell.api.auth.domain.EmailStanding
import net.blueshell.api.auth.domain.SecurityContacts
import net.blueshell.api.auth.domain.twofactor.PendingSecret
import net.blueshell.api.auth.domain.twofactor.TwoFactor
import net.blueshell.api.auth.domain.twofactor.TwoFactorStanding
import net.blueshell.api.auth.persistence.SecurityActorKind
import net.blueshell.api.auth.persistence.SecurityEvent
import net.blueshell.api.auth.persistence.SecurityEventKind
import net.blueshell.api.auth.persistence.TrustedBrowser
import net.blueshell.api.security.Browser
import net.blueshell.api.security.SignIn
import net.blueshell.api.security.SignInContext
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.UserPrincipal
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

class AccountSecurityControllerTest {
    private val accountSecurity = mock<AccountSecurity>()
    private val twoFactor = mock<TwoFactor>()
    private val controller = AccountSecurityController(
        accountSecurity,
        twoFactor,
        SecurityContacts("board@example.org", "https://api/discord/channel/board", "https://api/discord/channel/suggestions"),
    )
    private val signIn = SignIn("here", 7, Instant.EPOCH, Instant.EPOCH, Browser("Firefox", "Linux"), 0, "j", Instant.EPOCH)
    private val person =
        User(username = "alice", email = "a@example.com", password = "h", initials = "A", firstName = "Alice", lastName = "Doe").also {
            it.id = 7
        }

    @BeforeEach
    fun signIn() {
        val principal = UserPrincipal(7, "alice", "h", true, setOf(Role.MEMBER), null, null)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
        val request = MockHttpServletRequest().apply { setAttribute(SignInContext.ATTRIBUTE, signIn) }
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
    }

    @AfterEach
    fun signOut() {
        SecurityContextHolder.clearContext()
        RequestContextHolder.resetRequestAttributes()
    }

    @Test
    fun `the person's own two-factor goes through with their id and sign-in`() {
        whenever(twoFactor.standing(7)).thenReturn(TwoFactorStanding(on = true, backupCodesLeft = 3, required = false, offered = false))
        whenever(accountSecurity.setUpTwoFactor(7, "pw")).thenReturn(PendingSecret("otpauth://x", "KEY"))
        whenever(twoFactor.confirm(7, "123456")).thenReturn(listOf("a"))
        whenever(accountSecurity.regenerateBackupCodes(7)).thenReturn(listOf("b"))

        controller.stepUp(StepUpRequest(code = "123456"))
        assertThat(controller.twoFactorStanding()).isEqualTo(
            TwoFactorStandingResponse(on = true, backupCodesLeft = 3, required = false, offered = false, mayTurnOff = false, since = null),
        )
        assertThat(controller.setUpTwoFactor(TwoFactorSetUpRequest("pw"))).isEqualTo(TwoFactorSetupResponse("otpauth://x", "KEY"))
        whenever(accountSecurity.setUpTwoFactor(7, null)).thenReturn(PendingSecret("otpauth://y", "KEY2"))
        assertThat(controller.setUpTwoFactor(TwoFactorSetUpRequest()).key).isEqualTo("KEY2")
        assertThat(controller.confirmTwoFactor(CodeRequest("123456")).codes).containsExactly("a")
        controller.twoFactorSaved()
        controller.turnOffTwoFactor()
        assertThat(controller.regenerateBackupCodes().codes).containsExactly("b")
        controller.answerTwoFactorOffer()

        verify(accountSecurity).stepUp(7, "here", "123456", null)
        verify(twoFactor).saved(7, "here")
        verify(accountSecurity).turnOffTwoFactor(7)
        verify(twoFactor).answerOffer(7)
    }

    @Test
    fun `the password and the address go through`() {
        whenever(accountSecurity.emailOf(7)).thenReturn(EmailStanding("a@example.com", "b@example.com"))
        assertThat(controller.emailAddress()).isEqualTo(EmailAddressResponse("a@example.com", "b@example.com"))

        val change = PasswordChangeRequest("old", "Another123!")
        controller.changePassword(change)
        controller.requestEmailChange(EmailChangeRequest("new@example.com"))

        verify(accountSecurity).changePassword(7, "here", change.currentPassword, change.newPassword)
        verify(accountSecurity).requestEmailChange(7, "new@example.com")
    }

    @Test
    fun `sign-ins are listed with this one marked, and ended one at a time or all at once`() {
        val other = signIn.copy(id = "there", browser = Browser("Safari", "iOS"))
        whenever(accountSecurity.signInsOf(7)).thenReturn(listOf(signIn, other))
        whenever(accountSecurity.endSignIn(7, "there")).thenReturn(true)

        assertThat(controller.signIns()).containsExactly(
            SignInResponse("here", "Firefox", "Linux", Instant.EPOCH, Instant.EPOCH, current = true),
            SignInResponse("there", "Safari", "iOS", Instant.EPOCH, Instant.EPOCH, current = false),
        )
        controller.endSignIn("there")
        assertThrows<ResponseStatusException> { controller.endSignIn("nobody's") }
        controller.signOutEverywhere()
        controller.signOutElsewhere()

        verify(accountSecurity).signOutEverywhere(7)
        verify(accountSecurity).signOutElsewhere(7, "here")
    }

    @Test
    fun `trusted browsers are listed and forgotten`() {
        val browser = TrustedBrowser(person, "sel", "hash", "Firefox", "Linux", Instant.EPOCH, Instant.EPOCH, null).also { it.id = 3 }
        whenever(accountSecurity.trustedBrowsersOf(7)).thenReturn(listOf(browser))
        whenever(accountSecurity.forgetTrustedBrowser(7, 3)).thenReturn(true)

        assertThat(controller.trustedBrowsers()).containsExactly(
            TrustedBrowserResponse(3, "Firefox", "Linux", Instant.EPOCH, Instant.EPOCH, null),
        )
        controller.forgetTrustedBrowser(3)
        assertThrows<ResponseStatusException> { controller.forgetTrustedBrowser(4) }
        controller.forgetTrustedBrowsers()

        verify(accountSecurity).forgetTrustedBrowsers(7)
    }

    @Test
    fun `the log names who acted when it was somebody else`() {
        val admin =
            User(
                username = "root",
                email = "r@example.com",
                password = "h",
                initials = "R",
                firstName = "Ro",
                lastName = "Ot",
            ).also { it.id = 1 }
        val byAdmin = SecurityEvent(
            person,
            admin,
            SecurityActorKind.PERSON,
            SecurityEventKind.ACCOUNT_UNLOCKED,
            note = "why",
            occurredAt = Instant.EPOCH,
        )
        val own = SecurityEvent(
            person,
            person,
            SecurityActorKind.PERSON,
            SecurityEventKind.PASSWORD_CHANGED,
            null,
            "Firefox",
            "Linux",
            Instant.EPOCH,
        )
        byAdmin.id = 1
        own.id = 2
        val page = PageRequest.of(0, 20)
        whenever(accountSecurity.eventsOf(7, page)).thenReturn(PageImpl(listOf(byAdmin, own), page, 2))
        whenever(accountSecurity.eventsOf(9, page)).thenReturn(PageImpl(emptyList(), page, 0))

        val read = controller.mySecurityEvents(page)
        assertThat(read.events.map { it.actorName }).containsExactly("Ro Ot", null)
        assertThat(read.events.map { it.kind }).containsExactly(SecurityEventKind.ACCOUNT_UNLOCKED, SecurityEventKind.PASSWORD_CHANGED)
        assertThat(read.events[0].note).isEqualTo("why")
        assertThat(listOf(read.events[1].browser, read.events[1].platform)).containsExactly("Firefox", "Linux")
        assertThat(read.events[1].actorKind).isEqualTo(SecurityActorKind.PERSON)
        assertThat(read.events[1].occurredAt).isEqualTo(Instant.EPOCH)
        assertThat(read.events[1].id).isEqualTo(2)
        assertThat(listOf(read.totalElements, read.totalPages.toLong(), read.page.toLong())).containsExactly(2L, 1L, 0L)
        assertThat(controller.securityEvents(9, page).events).isEmpty()
    }

    @Test
    fun `an admin reads, resets, resends and unlocks for somebody else`() {
        whenever(accountSecurity.standingOf(9)).thenReturn(AccountStanding(twoFactorOn = true, awaitingReenrolment = false, locked = true))

        assertThat(controller.accountStanding(9)).isEqualTo(
            AccountStandingResponse(twoFactorOn = true, awaitingReenrolment = false, locked = true),
        )
        controller.resetTwoFactor(9, ReasonRequest("lost it"))
        controller.resendReenrolmentLink(9)
        controller.unlock(9, UnlockRequest("heard from them", "fixed@example.com"))
        controller.unlock(8, UnlockRequest("heard from them"))

        verify(accountSecurity).resetTwoFactorFor(7, 9, "lost it")
        verify(accountSecurity).resendReenrolmentLink(7, 9)
        verify(accountSecurity).unlock(7, 9, "heard from them", "fixed@example.com")
        verify(accountSecurity).unlock(7, 8, "heard from them", null)
    }

    @Test
    fun `the links a security email carries answer who to contact`() {
        assertThat(controller.lock(TokenRequest("sel.ver"))).isEqualTo(LockResponse("board@example.org"))
        controller.confirmEmailChange(TokenRequest("sel.ver2"))

        verify(accountSecurity).lockWithLink("sel.ver")
        verify(accountSecurity).confirmEmailChange("sel.ver2")
    }

    @Test
    fun `nothing is done for somebody not signed in`() {
        signOut()

        assertThrows<AccessDeniedException> { controller.signOutEverywhere() }
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UserPrincipal(7, "alice", "h", true, emptySet(), null, null), null, emptyList())
        assertThrows<AccessDeniedException> { controller.twoFactorSaved() }
    }
}
