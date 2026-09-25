package net.blueshell.api.system.frontend.auth

import com.microsoft.playwright.options.Cookie
import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.api.system.frontend.helper.LoginDomainHelper
import net.blueshell.api.system.frontend.helper.TestIdLocatorHelper
import net.blueshell.systemtests.PlaywrightTestBase
import net.blueshell.systemtests.TestHelper
import net.blueshell.systemtests.TotpCodes
import net.blueshell.systemtests.awaitResponseFrom
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/** Setting up two-factor on the security page, and signing in with it, as a member drives both. */
@Tag("system")
class TwoFactorSystemTest : PlaywrightTestBase() {
    private fun byTestId(id: String) = TestIdLocatorHelper.byTestId(page, id)

    private fun setUpTwoFactor(password: String): String {
        page.navigate("$frontendUrl/account/security")
        byTestId("security-two-factor").click()
        page.waitForURL("**/account/security/two-factor/set-up**")
        TestIdLocatorHelper.textInput(page, "two-factor-password-field").fill(password)
        byTestId("two-factor-start-btn").click()
        return scanAndSave()
    }

    /** From the QR code on: reads the key, gives a first code and saves the backup codes. */
    private fun scanAndSave(): String {
        byTestId("two-factor-qr").waitFor()
        val key = byTestId("two-factor-key").innerText().trim()
        byTestId("two-factor-scanned-btn").click()

        TestIdLocatorHelper.textInput(page, "two-factor-code-field").fill(TotpCodes.now(key))
        byTestId("two-factor-confirm-btn").click()
        byTestId("backup-codes").waitFor()
        assertThat(page.getByTestId("backup-code").count()).isEqualTo(10)
        byTestId("two-factor-saved-check").locator("input").check()
        byTestId("two-factor-finish-btn").click()
        return key
    }

    private fun passwordStep(user: TestHelper.RegisteredUser) {
        page.navigate("$frontendUrl/login/")
        LoginDomainHelper.fillLoginCredentials(page, user.username, user.password)
        page.awaitResponseFrom(
            control = LoginDomainHelper.loginSubmitButton(page),
            expected = "POST /auth",
        ) { it.url().contains("/auth") && it.request().method() == "POST" }
    }

    @Test
    fun `a member sets up two-factor, signs in with a code and trusts the browser`() {
        val member = TestHelper.registerAndActivate()
        assertThat(AuthHelper.submitLogin(page, frontendUrl, member.username, member.password)).isEqualTo(200)
        val key = setUpTwoFactor(member.password)
        page.waitForURL("**/account/security/two-factor")
        assertThat(byTestId("security-backup-codes-left").textContent()).contains("10 of 10 left")

        context.clearCookies()
        passwordStep(member)
        byTestId("login-code-form").waitFor()
        TotpCodes.awaitNextStep()
        TestIdLocatorHelper.textInput(page, "login-code-field").fill(TotpCodes.now(key))
        byTestId("login-trust-browser").locator("input").check()
        page.awaitResponseFrom(
            control = byTestId("login-code-submit-btn"),
            expected = "POST /auth/two-factor",
        ) { it.url().contains("/auth/two-factor") }
        page.waitForURL { !it.contains("/login") }

        val trusted = context.cookies().filter { it.name == TestHelper.TRUSTED_BROWSER_COOKIE }
        context.clearCookies()
        context.addCookies(trusted.map { Cookie(it.name, it.value).setDomain(it.domain).setPath(it.path) })
        passwordStep(member)
        page.waitForURL { !it.contains("/login") }
        assertThat(byTestId("login-code-form").count()).isZero()
    }

    @Test
    fun `a new board member signs in and sets up two-factor without giving the password again`() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        TestHelper.withoutTwoFactor(board.username)

        passwordStep(board)
        page.waitForURL("**/account/set-up-two-factor**")
        assertThat(byTestId("two-factor-password-field").count()).isZero()
        scanAndSave()

        page.waitForURL { !it.contains("/set-up-two-factor") }
        page.navigate("$frontendUrl/events")
        page.waitForURL("**/events")
    }

    @Test
    fun `a board member without two-factor is kept on the set-up whatever they open`() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        TestHelper.withoutTwoFactor(board.username)

        assertThat(AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)).isEqualTo(200)
        page.navigate("$frontendUrl/events")

        page.waitForURL("**/account/set-up-two-factor?redirect=**")
        byTestId("two-factor-set-up").waitFor()
        byTestId("two-factor-sign-out-btn").waitFor()
    }
}
