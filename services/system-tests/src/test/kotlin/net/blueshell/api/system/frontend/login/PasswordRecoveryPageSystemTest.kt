package net.blueshell.api.system.frontend.login

import net.blueshell.api.ApiApplication
import net.blueshell.api.config.TestCleanUpListener
import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.api.system.frontend.helper.LoginDomainHelper
import net.blueshell.systemtests.PlaywrightTestBase
import net.blueshell.systemtests.TestHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestExecutionListeners
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration

@Tag("system")
@ActiveProfiles("test")
@TestExecutionListeners(
    listeners = [TestCleanUpListener::class],
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS,
)
@SpringBootTest(
    classes = [ApiApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties = ["server.port=8080", "app.jobs.auto-dispatch=true"],
)
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
        pollFor("reset-password submit enabled") { !resetPasswordButton.isDisabled }

        page.waitForResponse("**/recovery/password") {
            resetPasswordButton.click()
        }

        page.locator("[data-testid='reset-password-success-state']").first().waitFor()

        val status = AuthHelper.submitLogin(page, frontendUrl, user.username, newPassword)
        assertThat(status).isEqualTo(200)
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
