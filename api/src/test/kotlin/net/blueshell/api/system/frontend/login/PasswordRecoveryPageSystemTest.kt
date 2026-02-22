package net.blueshell.api.system.frontend.login

import net.blueshell.api.domain.auth.application.factory.RecoveryTokenFactory
import net.blueshell.api.factory.user.persistence.UserFactory
import net.blueshell.api.shared.enums.ResetType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.system.frontend.FrontendSystemTestBase
import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.api.system.frontend.helper.LoginDomainHelper
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
            LoginDomainHelper.fillForgotPasswordUsername(page, user.username)
            LoginDomainHelper.forgotPasswordUsernameInput(page).press("Tab")

            val sendResetButton = LoginDomainHelper.forgotPasswordSubmitButton(page)
            waitFor(
                onTimeoutMessage = { "Expected forgot-password submit button to become enabled" }
            ) {
                !sendResetButton.isDisabled
            }

            page.waitForResponse("**/recovery/password/reset/**") {
                sendResetButton.click()
            }

            assertThat(
                page.locator("[data-testid='forgot-password-success-state']").count()
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
            LoginDomainHelper.fillResetPasswordForm(page, newPassword)
            LoginDomainHelper.resetPasswordRepeatInput(page).press("Tab")

            val resetPasswordButton = LoginDomainHelper.resetPasswordSubmitButton(page)
            waitFor(
                onTimeoutMessage = { "Expected reset-password submit button to become enabled" }
            ) {
                !resetPasswordButton.isDisabled
            }

            page.waitForResponse("**/recovery/password") {
                resetPasswordButton.click()
            }

            assertThat(
                page.locator("[data-testid='reset-password-success-state']").count()
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
