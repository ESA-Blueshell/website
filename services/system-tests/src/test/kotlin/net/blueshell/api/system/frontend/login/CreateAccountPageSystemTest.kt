package net.blueshell.api.system.frontend.login

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.api.system.frontend.helper.UserFormHelper
import net.blueshell.systemtests.PlaywrightTestBase
import net.blueshell.systemtests.TestHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("system")
class CreateAccountPageSystemTest : PlaywrightTestBase() {

    @Test
    fun `creates disabled account and sends activation email`() {
        val credentials = createAccountThroughUi(page, "$frontendUrl/account/create")

        val persisted = pollForUser(credentials.username)
        assertThat(persisted.email).isEqualTo(credentials.email)
        assertThat(persisted.enabled).isFalse()
        assertThat(TestHelper.findRoles(credentials.username)).contains("GUEST")

        TestHelper.assertEmailSent(credentials.email, "Activate your Account")
    }

    @Test
    fun `blocks login before activation`() {
        val credentials = createAccountThroughUi(page, "$frontendUrl/account/create")

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, credentials.username, credentials.password)
        assertThat(page.url()).contains("/login")
        assertThat(loginStatus).isEqualTo(401)

        val persisted = pollForUser(credentials.username)
        assertThat(persisted.enabled).isFalse()
        TestHelper.assertEmailSent(credentials.email, "Activate your Account")
    }

    private data class Credentials(val username: String, val email: String, val password: String)

    private fun createAccountThroughUi(page: Page, url: String): Credentials {
        val suffix = System.currentTimeMillis().toString().takeLast(8)
        val username = "sysuser$suffix"
        val email = "sysuser$suffix@example.com"
        val password = "Passw0rd!$suffix"
        val phoneNumber = "+3161${suffix.takeLast(7)}"

        page.navigate(url)
        UserFormHelper.fill(
            page = page,
            fields = UserFormHelper.Fields(
                initials = "SU",
                firstName = "System",
                surname = "User$suffix",
                username = username,
                discord = "sysuser$suffix",
                email = email,
                phoneNumber = phoneNumber,
                password = password,
                repeatedPassword = password,
            ),
        )

        if (UserFormHelper.acceptPrivacyConsentIfVisible(page)) {
            pollFor("privacy consent checkbox checked") {
                UserFormHelper.privacyConsentCheckbox(page).isChecked
            }
        }

        // Wait for the registration response before returning so the user is
        // fully persisted by the time the caller chains the next action.
        page.waitForResponse(
            { response ->
                response.request().method() == "POST" && response.url().endsWith("/signup")
            },
        ) {
            page.getByRole(
                AriaRole.BUTTON,
                Page.GetByRoleOptions().setName("Create Account").setExact(false),
            ).click()
        }

        return Credentials(username, email, password)
    }

    private fun pollForUser(username: String): TestHelper.RegisteredUserRow {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            val row = TestHelper.findUser(username)
            if (row != null) return row
            Thread.sleep(200)
        }
        throw AssertionError("Expected user '$username' to be persisted within 10s")
    }

    private fun pollFor(description: String, timeoutMs: Long = 5_000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            Thread.sleep(100)
        }
        throw AssertionError("Expected '$description' within ${timeoutMs}ms")
    }
}
