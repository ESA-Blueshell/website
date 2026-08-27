package net.blueshell.api.system.frontend.login

import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.api.system.frontend.helper.LoginDomainHelper
import net.blueshell.systemtests.PlaywrightTestBase
import net.blueshell.systemtests.TestHelper
import net.blueshell.systemtests.pollFor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat as assertPw

@Tag("system")
class PasswordRecoveryPageSystemTest : PlaywrightTestBase() {

    @Test
    fun `reset password allows login with new password`() {
        val user = TestHelper.registerActivateAndPromote("GUEST")
        val rawToken = TestHelper.mintRecoveryToken(
            username = user.username,
            type = "PASSWORD_RESET",
            ttl = Duration.ofHours(1),
        )
        val encodedToken = URLEncoder.encode(rawToken, StandardCharsets.UTF_8)
        val newPassword = "N3wPassw0rd!"

        page.navigate("$frontendUrl/account/reset-password#token=$encodedToken")
        LoginDomainHelper.fillResetPasswordForm(page, newPassword)
        LoginDomainHelper.resetPasswordRepeatInput(page).press("Tab")

        val resetPasswordButton = LoginDomainHelper.resetPasswordSubmitButton(page)
        // The button stays disabled until the repeat-password rule passes, so an
        // enabled button is the signal that the form will submit.
        assertPw(resetPasswordButton).isEnabled()

        page.waitForResponse("**/recovery/password") {
            resetPasswordButton.click()
        }

        page.locator("[data-testid='reset-password-success-state']").first().waitFor()

        val status = AuthHelper.submitLogin(page, frontendUrl, user.username, newPassword)
        assertThat(status).isEqualTo(200)
    }
}
