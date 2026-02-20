package net.blueshell.api.system.frontend.management

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import net.blueshell.api.factory.user.persistence.UserFactory
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.system.frontend.FrontendSystemTestBase
import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.api.system.frontend.helper.RecoveryManagerHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@Tag("system")
class RecoveryManagerPageSystemTest : FrontendSystemTestBase() {

    @Autowired
    private lateinit var userFactory: UserFactory

    @Test
    fun `recovery manager resends activation for inactive user`() {
        val board = userFactory.createUserWithRole(Role.BOARD, enabled = true)
        val inactiveUser = userFactory.createUserWithRole(Role.GUEST, enabled = false)

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            RecoveryManagerHelper.open(page, frontendUrl)

            RecoveryManagerHelper.openSection(page, "Inactive accounts")
            RecoveryManagerHelper.searchUser(page, inactiveUser.username)

            waitFor(
                onTimeoutMessage = { "Expected inactive user ${inactiveUser.username} to be visible" }
            ) {
                page.getByText(inactiveUser.username).count() > 0
            }

            val response = page.waitForResponse("**/recovery/user/activate/resend/**") {
                page.getByRole(
                    AriaRole.BUTTON,
                    Page.GetByRoleOptions().setName("Resend Activation Email").setExact(false)
                ).click()
            }
            assertThat(response.status()).isEqualTo(204)
        }

        assertEmailSent(inactiveUser.email, "Activate your Account")
    }

    @Test
    fun `recovery manager sends password reset for active user`() {
        val board = userFactory.createUserWithRole(Role.BOARD, enabled = true)
        val activeUser = userFactory.createUserWithRole(Role.GUEST, enabled = true)

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            RecoveryManagerHelper.open(page, frontendUrl)

            RecoveryManagerHelper.openSection(page, "Active accounts")
            RecoveryManagerHelper.searchUser(page, activeUser.username)

            waitFor(
                onTimeoutMessage = { "Expected active user ${activeUser.username} to be visible" }
            ) {
                page.getByText(activeUser.username).count() > 0
            }

            val response = page.waitForResponse("**/recovery/password/reset/**") {
                page.getByRole(
                    AriaRole.BUTTON,
                    Page.GetByRoleOptions().setName("Send Password Reset Email").setExact(false)
                ).click()
            }
            assertThat(response.status()).isEqualTo(204)
        }

        assertEmailSent(activeUser.email, "Reset Your Blueshell Account Password")
    }

    private companion object {
        const val DEFAULT_PASSWORD = "Password123!"
    }
}
