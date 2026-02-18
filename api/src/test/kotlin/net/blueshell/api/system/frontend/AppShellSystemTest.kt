package net.blueshell.api.system.frontend

import com.microsoft.playwright.Page
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat as assertPw
import com.microsoft.playwright.options.AriaRole
import net.blueshell.api.factory.user.persistence.UserFactory
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.system.frontend.helper.AuthHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@Tag("system")
class AppShellSystemTest : FrontendSystemTestBase() {

    @Autowired
    private lateinit var userFactory: UserFactory

    @Test
    fun `desktop nav routes to main public pages`() {
        withPage { page ->
            page.setViewportSize(1600, 1000)
            page.navigate("$frontendUrl/")

            page.getByText("Membership", Page.GetByTextOptions().setExact(true)).first().click()
            page.waitForURL("**/membership")

            page.getByText("Events", Page.GetByTextOptions().setExact(true)).first().click()
            page.waitForURL("**/events")

            page.getByText("Contact", Page.GetByTextOptions().setExact(true)).first().click()
            page.waitForURL("**/contact")
        }
    }

    @Test
    fun `mobile drawer routes to public page`() {
        withPage { page ->
            page.setViewportSize(600, 900)
            page.navigate("$frontendUrl/")

            page.locator("header button").first().click()

            page.evaluate(
                """
                () => {
                  const link = document.querySelector(".v-navigation-drawer [href='/contact']")
                  if (!(link instanceof HTMLElement)) throw new Error("Drawer contact link not found")
                  link.click()
                }
                """.trimIndent()
            )
            page.waitForURL("**/contact")
        }
    }

    @Test
    fun `cookie banner can be accepted`() {
        withPage { page ->
            page.navigate("$frontendUrl/")

            val cookieMessage = page.getByText(
                "We're using cookies to keep you logged in.",
                Page.GetByTextOptions().setExact(false)
            )
            assertPw(cookieMessage).isVisible()

            page.getByRole(
                AriaRole.BUTTON,
                Page.GetByRoleOptions().setName("Got it")
            ).click()
            assertPw(cookieMessage).hasCount(0)

            page.reload()
            assertPw(cookieMessage).hasCount(0)
        }
    }

    @Test
    fun `dark mode preference persists after toggle`() {
        withPage { page ->
            page.navigate("$frontendUrl/")

            val initialValue = page.evaluate("() => localStorage.getItem('esa-blueshell.nl:darkMode')") as String?

            page.evaluate(
                """
                () => {
                  const toggle = document.querySelector('button.roll-on, button.roll-off')
                  if (!(toggle instanceof HTMLElement)) throw new Error('Dark mode toggle button not found')
                  toggle.click()
                }
                """.trimIndent()
            )

            waitFor(
                onTimeoutMessage = { "Expected dark mode preference to change after toggling dark mode button" }
            ) {
                (page.evaluate("() => localStorage.getItem('esa-blueshell.nl:darkMode')") as String?) != initialValue
            }

            val updatedValue = page.evaluate("() => localStorage.getItem('esa-blueshell.nl:darkMode')") as String?
            page.reload()
            val persistedValue = page.evaluate("() => localStorage.getItem('esa-blueshell.nl:darkMode')") as String?

            assertThat(persistedValue).isEqualTo(updatedValue)
        }
    }

    @Test
    fun `logout clears session and protected routes redirect to login`() {
        val member = userFactory.createUserWithRole(Role.MEMBER, enabled = true)

        withPage { page ->
            page.setViewportSize(1600, 1000)
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, member.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            openAccountMenu(page)
            page.getByText("Account", Page.GetByTextOptions().setExact(true)).click()
            page.waitForURL("**/account")

            openAccountMenu(page)
            page.getByText("Log Out", Page.GetByTextOptions().setExact(true)).click()
            page.waitForURL("**/")

            waitFor(
                onTimeoutMessage = { "Expected login cookie to be cleared after logging out" }
            ) {
                page.context().cookies().none { it.name == "login" }
            }

            page.evaluate(
                """
                async () => {
                  const routerModule = await import('/src/plugins/router.ts')
                  await routerModule.default.push('/account')
                }
                """.trimIndent()
            )
            waitFor(
                timeoutMs = 10_000,
                onTimeoutMessage = { "Expected anonymous user to be redirected to /login for /account" }
            ) {
                page.url().contains("/login")
            }
            assertThat(page.url()).contains("redirect=/account")
        }
    }

    @Test
    fun `board sees management menu and cannot access admin-only jobs`() {
        val board = userFactory.createUserWithRole(Role.BOARD, enabled = true)

        withPage { page ->
            page.setViewportSize(1600, 1000)
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            openManagementMenu(page)

            assertPw(page.getByText("Manage addresses", Page.GetByTextOptions().setExact(true))).isVisible()
            assertPw(page.getByText("Manage account recovery", Page.GetByTextOptions().setExact(true))).isVisible()
            assertPw(page.getByText("Manage committees", Page.GetByTextOptions().setExact(true))).isVisible()
            assertPw(page.getByText("Manage contributions", Page.GetByTextOptions().setExact(true))).isVisible()
            assertPw(page.getByText("Manage members", Page.GetByTextOptions().setExact(true))).isVisible()
            assertPw(page.getByText("Manage jobs", Page.GetByTextOptions().setExact(true))).hasCount(0)

            page.navigate("$frontendUrl/management/jobs")
            page.waitForURL("**/")
        }
    }

    @Test
    fun `admin can open jobs manager`() {
        val admin = userFactory.createUserWithRole(Role.ADMIN, enabled = true)

        withPage { page ->
            page.setViewportSize(1600, 1000)
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, admin.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            openManagementMenu(page)

            page.getByText("Manage jobs", Page.GetByTextOptions().setExact(true)).click()
            page.waitForURL("**/management/jobs")
            assertPw(page.getByText("Job Manager", Page.GetByTextOptions().setExact(false))).isVisible()
        }
    }

    @Test
    fun `auth guard redirects anonymous user with redirect query`() {
        withPage { page ->
            page.navigate("$frontendUrl/")
            page.evaluate(
                """
                async () => {
                  const routerModule = await import('/src/plugins/router.ts')
                  await routerModule.default.push('/members/manage')
                }
                """.trimIndent()
            )
            waitFor(
                timeoutMs = 10_000,
                onTimeoutMessage = { "Expected anonymous user to be redirected to /login for /members/manage" }
            ) {
                page.url().contains("/login")
            }
            assertThat(page.url()).contains("redirect=/members/manage")
        }
    }

    @Test
    fun `route redirects for esports and events calendar work`() {
        withPage { page ->
            page.navigate("$frontendUrl/esports")
            page.waitForURL("**/esports/competitive-scene")

            page.navigate("$frontendUrl/events/calendar")
            page.waitForURL("**/events")
            assertThat(page.url()).doesNotContain("/events/calendar")
        }
    }

    private companion object {
        const val DEFAULT_PASSWORD = "Password123!"
    }

    private fun openManagementMenu(page: Page) {
        val headerButtons = page.locator("header button")
        waitFor(
            timeoutMs = 10_000,
            onTimeoutMessage = { "Expected management menu button to be visible for board/admin user" }
        ) {
            headerButtons.count() >= 2
        }
        val managementMenuButton = headerButtons.nth(headerButtons.count() - 2)
        managementMenuButton.click()
    }

    private fun openAccountMenu(page: Page) {
        val headerButtons = page.locator("header button")
        waitFor(
            timeoutMs = 10_000,
            onTimeoutMessage = { "Expected account menu button to be visible after login" }
        ) {
            headerButtons.count() >= 1
        }
        val accountMenuButton = headerButtons.nth(headerButtons.count() - 1)
        accountMenuButton.click()
    }
}
