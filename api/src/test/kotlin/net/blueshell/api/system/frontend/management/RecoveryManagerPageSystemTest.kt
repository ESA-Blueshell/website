package net.blueshell.api.system.frontend.management

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat as assertPw
import net.blueshell.api.factory.user.persistence.UserFactory
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.system.frontend.FrontendSystemTestBase
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
            val loginStatus = loginThroughUi(page, board.username, DEFAULT_PASSWORD)
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
            val loginStatus = loginThroughUi(page, board.username, DEFAULT_PASSWORD)
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

    @Test
    fun `recovery manager filters inactive accounts by multiple fields`() {
        val board = userFactory.createUserWithRole(Role.BOARD, enabled = true)
        val suffix = System.currentTimeMillis().toString().takeLast(6)
        val filteredUser = userFactory.createUserWithRole(Role.GUEST, enabled = false).apply {
            firstName = "Inactive$suffix"
            lastName = "Filter"
            discord = "inactive-filter-$suffix"
            email = "inactive.filter.$suffix@test.com"
        }
        userRepository.saveAndFlush(filteredUser)
        val otherUser = userFactory.createUserWithRole(Role.GUEST, enabled = false)

        withPage { page ->
            val loginStatus = loginThroughUi(page, board.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            RecoveryManagerHelper.open(page, frontendUrl)
            RecoveryManagerHelper.openSection(page, "Inactive accounts")
            RecoveryManagerHelper.searchUser(page, "${filteredUser.firstName} inactive.filter.$suffix")

            waitFor(
                onTimeoutMessage = { "Expected filtered inactive user ${filteredUser.username} to be visible" }
            ) {
                page.getByText(filteredUser.username, Page.GetByTextOptions().setExact(true)).count() > 0
            }
            assertPw(page.getByText(otherUser.username, Page.GetByTextOptions().setExact(true))).hasCount(0)
        }
    }

    @Test
    fun `recovery manager filters active accounts by multiple fields`() {
        val board = userFactory.createUserWithRole(Role.BOARD, enabled = true)
        val suffix = System.currentTimeMillis().toString().takeLast(6)
        val filteredUser = userFactory.createUserWithRole(Role.GUEST, enabled = true).apply {
            firstName = "Active$suffix"
            lastName = "Filter"
            discord = "active-filter-$suffix"
            email = "active.filter.$suffix@test.com"
        }
        userRepository.saveAndFlush(filteredUser)
        val otherUser = userFactory.createUserWithRole(Role.GUEST, enabled = true)

        withPage { page ->
            val loginStatus = loginThroughUi(page, board.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            RecoveryManagerHelper.open(page, frontendUrl)
            RecoveryManagerHelper.openSection(page, "Active accounts")
            RecoveryManagerHelper.searchUser(page, "${filteredUser.firstName} active.filter.$suffix")

            waitFor(
                onTimeoutMessage = { "Expected filtered active user ${filteredUser.username} to be visible" }
            ) {
                page.getByText(filteredUser.username, Page.GetByTextOptions().setExact(true)).count() > 0
            }
            assertPw(page.getByText(otherUser.username, Page.GetByTextOptions().setExact(true))).hasCount(0)
        }
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
