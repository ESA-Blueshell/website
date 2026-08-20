package net.blueshell.api.system.frontend.login

import com.microsoft.playwright.Page
import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.api.system.frontend.helper.LoginDomainHelper
import net.blueshell.systemtests.PlaywrightTestBase
import net.blueshell.systemtests.TestHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration

@Tag("system")
class ActivationPageSystemTest : PlaywrightTestBase() {

    @Test
    fun `member activation activates account and allows sign in`() {
        val user = TestHelper.register()
        TestHelper.replaceRoles(user.username, setOf("MEMBER"))
        // Disabled by default — the activation flow flips enabled=true.
        val rawToken = TestHelper.mintRecoveryToken(
            username = user.username,
            type = "MEMBER_ACTIVATION",
            ttl = Duration.ofDays(7),
        )
        val encodedToken = URLEncoder.encode(rawToken, StandardCharsets.UTF_8)
        val newUsername = "member${System.currentTimeMillis().toString().takeLast(8)}"
        val newPassword = "N3wMemberPass!"

        page.navigate("$frontendUrl/account/activate/member#token=$encodedToken")
        submitMemberActivationForm(page, username = newUsername, password = newPassword)

        val response = page.waitForResponse("**/recovery/member/activate") {
            LoginDomainHelper.clickActivateMemberSubmit(page)
        }
        assertThat(response.status()).isEqualTo(200)
        page.locator("[data-testid='activate-member-success-alert']").first().waitFor()

        pollFor("user ${user.username} enabled after activation") {
            TestHelper.findUser(newUsername)?.enabled == true
        }

        val status = AuthHelper.submitLogin(page, frontendUrl, newUsername, newPassword)
        assertThat(status).isEqualTo(200)
    }

    @Test
    fun `member activation shows error for invalid token`() {
        val user = TestHelper.register()
        TestHelper.replaceRoles(user.username, setOf("MEMBER"))
        val invalidToken = URLEncoder.encode("invalid-member-token", StandardCharsets.UTF_8)
        val newUsername = "member${System.currentTimeMillis().toString().takeLast(8)}"
        val newPassword = "N3wMemberPass!"

        page.navigate("$frontendUrl/account/activate/member#token=$invalidToken")
        submitMemberActivationForm(page, username = newUsername, password = newPassword)

        val response = page.waitForResponse("**/recovery/member/activate") {
            LoginDomainHelper.clickActivateMemberSubmit(page)
        }
        assertThat(response.status()).isGreaterThanOrEqualTo(400)
        page.locator("[data-testid='activate-member-error-alert']").first().waitFor()

        assertThat(TestHelper.findUser(user.username)!!.enabled).isFalse()
    }

    @Test
    fun `user activation enables the account and reports whether the membership started`() {
        // Enable briefly so `attachMemberProfile` can POST via a real
        // login, then flip back to disabled so the activation flow has
        // work to do.
        val user = TestHelper.registerAndActivate()
        TestHelper.replaceRoles(user.username, setOf("MEMBER"))
        TestHelper.attachMemberProfile(user)
        TestHelper.setEnabled(user.username, false)
        val rawToken = TestHelper.mintRecoveryToken(
            username = user.username,
            type = "USER_ACTIVATION",
            ttl = Duration.ofHours(1),
        )
        val encodedToken = URLEncoder.encode(rawToken, StandardCharsets.UTF_8)

        val response = page.waitForResponse("**/recovery/user/activate") {
            page.navigate("$frontendUrl/account/activate/user#token=$encodedToken")
        }
        assertThat(response.status()).isEqualTo(200)
        // Confirming an address is not an application, so no membership starts here.
        assertThat(response.text()).contains("\"membershipStarted\":false")
        page.locator("[data-testid='activate-user-success-state']").first().waitFor()

        pollFor("user ${user.username} enabled after activation") {
            TestHelper.findUser(user.username)?.enabled == true
        }
    }

    @Test
    fun `user activation with invalid token shows warning`() {
        val user = TestHelper.register()
        TestHelper.replaceRoles(user.username, setOf("GUEST"))
        val invalidToken = URLEncoder.encode("invalid-user-token", StandardCharsets.UTF_8)

        val response = page.waitForResponse("**/recovery/user/activate") {
            page.navigate("$frontendUrl/account/activate/user#token=$invalidToken")
        }
        assertThat(response.status()).isGreaterThanOrEqualTo(400)
        page.locator("[data-testid='activate-user-error-alert']").first().waitFor()

        assertThat(TestHelper.findUser(user.username)!!.enabled).isFalse()
    }

    private fun submitMemberActivationForm(page: Page, username: String, password: String) {
        LoginDomainHelper.fillActivateMemberForm(page, username, password)
        LoginDomainHelper.activateMemberRepeatPasswordInput(page).press("Tab")
    }

    private fun pollFor(description: String, timeoutMs: Long = 10_000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            Thread.sleep(200)
        }
        throw AssertionError("Expected '$description' within ${timeoutMs}ms")
    }
}
