package net.blueshell.api.system.frontend.auth

import com.microsoft.playwright.Locator
import net.blueshell.acceptance.AcceptanceApi
import net.blueshell.acceptance.Inbox
import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.api.system.frontend.helper.LoginDomainHelper
import net.blueshell.api.system.frontend.helper.TestIdLocatorHelper
import net.blueshell.api.system.frontend.helper.UserManagerHelper
import net.blueshell.systemtests.PlaywrightTestBase
import net.blueshell.systemtests.TestHelper
import net.blueshell.systemtests.TotpCodes
import net.blueshell.systemtests.awaitResponseFrom
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Duration

/** The rest of account security as a person meets it in the browser: the offer, backup codes, the pages the emails open. */
@Tag("system")
class AccountSecuritySystemTest : PlaywrightTestBase() {
    private fun byTestId(id: String) = TestIdLocatorHelper.byTestId(page, id)

    private fun passwordStep(user: TestHelper.RegisteredUser) {
        context.clearCookies()
        page.navigate("$frontendUrl/login/")
        LoginDomainHelper.fillLoginCredentials(page, user.username, user.password)
        page.awaitResponseFrom(
            control = LoginDomainHelper.loginSubmitButton(page),
            expected = "POST /auth",
        ) { it.url().contains("/auth") && it.request().method() == "POST" }
    }

    @Test
    fun `the offer is made once, and not again after Not now`() {
        val member = TestHelper.registerAndActivate()

        passwordStep(member)
        page.waitForURL("**/account/two-factor**")
        byTestId("two-factor-offer-decline-btn").click()
        page.waitForURL { !it.contains("/account/two-factor") }

        passwordStep(member)
        page.waitForURL { !it.contains("/login") }
        assertThat(page.url()).doesNotContain("/account/two-factor")
    }

    @Test
    fun `a backup code signs in in place of the app`() {
        val member = TestHelper.registerAndActivate()
        val (_, codes) = AcceptanceApi.setUpTwoFactorWithBackupCodes(member)

        passwordStep(member)
        byTestId("login-code-form").waitFor()
        byTestId("login-use-backup-code-btn").click()
        TestIdLocatorHelper.textInput(page, "login-code-field").fill(codes.first())
        page.awaitResponseFrom(
            control = byTestId("login-code-submit-btn"),
            expected = "POST /auth/two-factor",
        ) { it.url().contains("/auth/two-factor") }
        page.waitForURL { !it.contains("/login") }
    }

    @Test
    fun `a lock link from a notification locks the account and names who to contact`() {
        val member = TestHelper.registerAndActivate()
        AcceptanceApi.changePassword(member, "Another123!pass")
        val email = Inbox.await(member.email, "Security notification")

        page.navigate("$frontendUrl/account/lock#token=${AcceptanceApi.linkToken(email.htmlContent, "account/lock")}")

        assertThat(byTestId("lock-account-contact-email").innerText()).contains("@")
        val refused = AcceptanceApi.attemptSignIn(member.copy(password = "Another123!pass"))
        assertThat(refused.asString()).contains("AccountLocked")
    }

    @Test
    fun `moving to another address on the security page waits for the new inbox`() {
        val member = TestHelper.registerAndActivate()
        val newAddress = "moved-${member.username}@example.com"
        assertThat(AuthHelper.submitLogin(page, frontendUrl, member.username, member.password)).isEqualTo(200)

        page.navigate("$frontendUrl/account/security")
        byTestId("security-email").click()
        TestIdLocatorHelper.textInput(page, "security-new-email-field").fill(newAddress)
        page.awaitResponseFrom(
            control = byTestId("security-change-email-btn"),
            expected = "POST /users/me/email",
        ) { it.url().contains("/users/me/email") }
        val email = Inbox.await(newAddress, "Confirm your new email address")

        page.navigate("$frontendUrl/account/confirm-email#token=${AcceptanceApi.linkToken(email.htmlContent, "account/confirm-email")}")

        byTestId("confirm-email-done").waitFor()
    }

    @Test
    fun `changing the password on its own page shows in the security log`() {
        val member = TestHelper.registerAndActivate()
        assertThat(AuthHelper.submitLogin(page, frontendUrl, member.username, member.password)).isEqualTo(200)

        page.navigate("$frontendUrl/account/security")
        byTestId("security-password").click()
        TestIdLocatorHelper.textInput(page, "security-current-password-field").fill(member.password)
        TestIdLocatorHelper.textInput(page, "security-new-password-field").fill("Another123!pass")
        page.awaitResponseFrom(
            control = byTestId("security-change-password-btn"),
            expected = "PUT /users/me/password",
        ) { it.url().contains("/users/me/password") }

        page.navigate("$frontendUrl/account/security/log")
        assertThat(byTestId("security-log-day").first().textContent()).isEqualTo("Today")
        byTestId("security-log-entry").filter(Locator.FilterOptions().setHasText("Password changed")).waitFor()
        assertThat(AcceptanceApi.attemptSignIn(member.copy(password = "Another123!pass")).statusCode).isEqualTo(200)
    }

    @Test
    fun `two tabs stay signed in across a cookie rotation, and signing out everywhere ends both`() {
        val member = TestHelper.registerAndActivate()
        assertThat(AuthHelper.submitLogin(page, frontendUrl, member.username, member.password)).isEqualTo(200)
        page.navigate("$frontendUrl/account/security/sign-ins")
        byTestId("security-sign-in").first().waitFor()
        val second = context.newPage()
        second.navigate("$frontendUrl/account/security/sign-ins")
        TestIdLocatorHelper.byTestId(second, "security-sign-in").first().waitFor()

        try {
            AcceptanceApi.advanceClock(Duration.ofMinutes(6).seconds)
            page.reload()
            second.reload()
            byTestId("security-sign-in").first().waitFor()
            TestIdLocatorHelper.byTestId(second, "security-sign-in").first().waitFor()

            byTestId("security-sign-out-everywhere-btn").click()
            page.waitForURL("**/login**")
            second.reload()
            second.waitForURL("**/login**")
        } finally {
            AcceptanceApi.resetClock()
            second.close()
        }
    }

    @Test
    fun `an admin resets somebody's two-factor from the user manager`() {
        val member = TestHelper.registerAndActivate()
        AcceptanceApi.setUpTwoFactor(member)
        val admin = TestHelper.registerActivateAndPromote("ADMIN")
        assertThat(AuthHelper.submitLogin(page, frontendUrl, admin.username, admin.password)).isEqualTo(200)
        val memberId = requireNotNull(TestHelper.findUser(member.username)).id

        UserManagerHelper.open(page, frontendUrl)
        UserManagerHelper.search(page, member.username)
        byTestId("member-manager-account-security-btn-$memberId").click()
        byTestId("account-security-two-factor-chip").waitFor()
        byTestId("account-security-reason-field").locator("textarea").first().fill("lost the phone and the codes")
        page.awaitResponseFrom(
            control = byTestId("account-security-reset-btn"),
            expected = "POST /users/{id}/two-factor/reset",
        ) { it.url().contains("/two-factor/reset") }

        byTestId("account-security-awaiting-chip").waitFor()
        Inbox.await(member.email, "set up two-factor again")
        TotpCodes.awaitNextStep()
    }
}
