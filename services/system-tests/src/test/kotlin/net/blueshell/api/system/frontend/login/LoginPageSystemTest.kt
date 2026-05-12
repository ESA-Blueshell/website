package net.blueshell.api.system.frontend.login

import com.microsoft.playwright.Page
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat as assertPw
import net.blueshell.api.ApiApplication
import net.blueshell.api.config.TestCleanUpListener
import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.systemtests.PlaywrightTestBase
import net.blueshell.systemtests.TestHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestExecutionListeners

/**
 * Worked example of a test that talks to the api strictly over HTTP +
 * JDBC through `TestHelper`, even though the api itself runs inside the
 * test JVM via `@SpringBootTest`. The Spring context exists to host
 * `ApiApplication` on `localhost:8080`; the test body never injects
 * beans or reaches into repositories. Once CI runs against a
 * containerised api the four bootstrap annotations come off.
 */
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
