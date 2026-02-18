package net.blueshell.api.system.frontend.management

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import net.blueshell.api.factory.user.persistence.UserFactory
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.system.frontend.FrontendSystemTestBase
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
            val loginStatus = loginThroughUi(page, board.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            page.navigate("$frontendUrl/recovery/manage")
            page.waitForURL("**/recovery/manage**")

            page.getByText("Inactive accounts", Page.GetByTextOptions().setExact(true)).click()
            page.getByRole(AriaRole.TEXTBOX).first().fill(inactiveUser.username)

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
            val loginStatus = loginThroughUi(page, board.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            page.navigate("$frontendUrl/recovery/manage")
            page.waitForURL("**/recovery/manage**")

            page.getByText("Active accounts", Page.GetByTextOptions().setExact(true)).click()
            page.getByRole(AriaRole.TEXTBOX).first().fill(activeUser.username)

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

    private fun loginThroughUi(page: Page, username: String, password: String): Int {
        page.navigate("$frontendUrl/login/")
        page.getByLabel("Username").fill(username)
        page.getByRole(
            AriaRole.TEXTBOX,
            Page.GetByRoleOptions().setName("Password")
        ).fill(password)

        val response = page.waitForResponse("**/auth") {
            page.getByRole(
                AriaRole.BUTTON,
                Page.GetByRoleOptions().setName("Login")
            ).click()
        }
        return response.status()
    }

    private companion object {
        const val DEFAULT_PASSWORD = "Password123!"
    }
}
