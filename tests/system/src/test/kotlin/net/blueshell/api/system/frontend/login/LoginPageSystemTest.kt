package net.blueshell.api.system.frontend.login

import com.microsoft.playwright.Page
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat as assertPw
import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.systemtests.PlaywrightTestBase
import net.blueshell.systemtests.TestHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Worked example of a test that talks to the api strictly over HTTP +
 * JDBC through `TestHelper`. The api itself runs as a docker-compose
 * service on `localhost:8080`; the test body never injects beans or
 * reaches into repositories.
 */
@Tag("system")
class LoginPageSystemTest : PlaywrightTestBase() {

    @Test
    fun `disabled account shows login error`() {
        val user = TestHelper.register()
        TestHelper.replaceRoles(user.username, setOf("MEMBER"))
        // No setEnabled(true) — disabled is the post-register default
        // and the point of this test.

        val status = AuthHelper.submitLogin(page, frontendUrl, user.username, user.password)
        assertThat(status).isEqualTo(401)
        assertLoginError(page)
    }

    @Test
    fun `wrong password shows login error`() {
        val user = TestHelper.registerActivateAndPromote("MEMBER")

        val status = AuthHelper.submitLogin(page, frontendUrl, user.username, "${user.password}x")
        assertThat(status).isEqualTo(401)
        assertLoginError(page)
    }

    @Test
    fun `enabled account logs in with correct password`() {
        val user = TestHelper.registerActivateAndPromote("MEMBER")

        val status = AuthHelper.submitLogin(page, frontendUrl, user.username, user.password)
        assertThat(status).isEqualTo(200)
    }

    private fun assertLoginError(page: Page) {
        assertPw(
            page.getByText(
                "Incorrect login credentials. Please double check your username and password.",
                Page.GetByTextOptions().setExact(true),
            ),
        ).isVisible()
    }
}
