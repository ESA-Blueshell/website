package net.blueshell.api.system.frontend.login

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import net.blueshell.api.domain.auth.application.factory.RecoveryTokenFactory
import net.blueshell.api.factory.user.persistence.UserFactory
import net.blueshell.api.shared.enums.ResetType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.system.frontend.FrontendSystemTestBase
import net.blueshell.api.system.frontend.helper.AuthHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import net.blueshell.api.domain.user.persistence.User
import java.time.Duration
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Tag("system")
class PasswordRecoveryPageSystemTest : FrontendSystemTestBase() {

    @Autowired
    private lateinit var userFactory: UserFactory

    @Autowired
    private lateinit var recoveryTokenFactory: RecoveryTokenFactory

    @Test
    fun `forgot password sends reset email`() {
        val user = createRecoveryUser()

        withPage { page ->
            page.navigate("$frontendUrl/login/forgor")
            page.getByLabel("Username").fill(user.username)
            page.getByLabel("Username").press("Tab")

            val sendResetButton = page.getByRole(
                AriaRole.BUTTON,
                Page.GetByRoleOptions().setName("Send reset mail").setExact(false)
            )
            waitFor(
                onTimeoutMessage = { "Expected forgot-password submit button to become enabled" }
            ) {
                !sendResetButton.isDisabled
            }

            page.waitForResponse("**/recovery/password/reset/**") {
                sendResetButton.click()
            }

            assertThat(
                page.getByText("If an account with that username exists, you’ll receive an email").count()
            ).isGreaterThan(0)
        }

        assertEmailSent(user.email, "Reset Your Blueshell Account Password")
    }

    @Test
    fun `reset password allows login with new password`() {
        val user = createRecoveryUser()
        val rawToken = recoveryTokenFactory.issue(
            user = user,
            type = ResetType.PASSWORD_RESET,
            ttl = Duration.ofHours(1)
        )
        val encodedToken = URLEncoder.encode(rawToken, StandardCharsets.UTF_8)
        val newPassword = "N3wPassw0rd!"

        withPage { page ->
            page.navigate("$frontendUrl/account/reset-password#token=$encodedToken")
            page.getByLabel("New Password").first().fill(newPassword)
            page.getByLabel("Repeat New Password").first().fill(newPassword)
            page.getByLabel("Repeat New Password").first().press("Tab")

            val resetPasswordButton = page.getByRole(
                AriaRole.BUTTON,
                Page.GetByRoleOptions().setName("Reset Password").setExact(false)
            )
            waitFor(
                onTimeoutMessage = { "Expected reset-password submit button to become enabled" }
            ) {
                !resetPasswordButton.isDisabled
            }

            page.waitForResponse("**/recovery/password") {
                resetPasswordButton.click()
            }

            assertThat(
                page.getByText("Your password has been reset successfully.").count()
            ).isGreaterThan(0)
        }

        withPage { page ->
            val status = AuthHelper.submitLogin(page, frontendUrl, user.username, newPassword)
            assertThat(status).isEqualTo(200)
        }
    }

    private fun createRecoveryUser(): User {
        val user = userFactory.createUserWithRole(Role.GUEST, enabled = true)
        val suffix = System.currentTimeMillis().toString()
        val username = "recover$suffix"
        user.username = username
        user.email = "$username@test.com"
        user.discord = "${username}0001"
        userRepository.saveAndFlush(user)
        return user
    }
}
