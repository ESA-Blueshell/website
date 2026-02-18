package net.blueshell.api.system.frontend.login

import com.microsoft.playwright.Page
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat as assertPw
import com.microsoft.playwright.options.AriaRole
import net.blueshell.api.factory.user.persistence.UserFactory
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.system.frontend.FrontendSystemTestBase
import net.blueshell.api.system.frontend.helper.AuthHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@Tag("system")
class LoginPageSystemTest : FrontendSystemTestBase() {
    @Autowired
    private lateinit var userFactory: UserFactory

    private val loginPassword = "Password123!"

    @Test
    fun `disabled account shows login error`() {
        withPage { page ->
            val user = createLoginUser(enabled = false)
            val status = AuthHelper.submitLogin(page, frontendUrl, user.username, loginPassword)
            assertThat(status).isEqualTo(401)
            assertLoginError(page)
        }
    }

    @Test
    fun `wrong password shows login error`() {
        withPage { page ->
            val user = createLoginUser(enabled = true)
            val status = AuthHelper.submitLogin(page, frontendUrl, user.username, "${loginPassword}x")
            assertThat(status).isEqualTo(401)
            assertLoginError(page)
        }
    }

    @Test
    fun `enabled account logs in with correct password`() {
        withPage { page ->
            val user = createLoginUser(enabled = true)
            val status = AuthHelper.submitLogin(page, frontendUrl, user.username, loginPassword)
            assertThat(status).isEqualTo(200)
        }
    }

    @Test
    fun `create account button opens account creation page`() {
        withPage { page ->
            page.navigate("$frontendUrl/login/")
            page.getByRole(
                AriaRole.LINK,
                Page.GetByRoleOptions().setName("Create Account").setExact(false)
            ).click()

            page.waitForURL("**/account/create**")
            assertThat(page.url()).contains("/account/create")
        }
    }

    @Test
    fun `forgot password keeps typed username in the field`() {
        withPage { page ->
            val username = "forgot_${System.currentTimeMillis()}"
            page.navigate("$frontendUrl/login/")
            page.getByLabel("Username").fill(username)

            val forgotPasswordButton = page.getByText(
                "forgot password?",
                Page.GetByTextOptions().setExact(false)
            )
            assertPw(forgotPasswordButton).isVisible()
            forgotPasswordButton.click()
            page.navigate("$frontendUrl/login/forgor?username=$username")

            val usernameField = page.getByRole(AriaRole.TEXTBOX).first()
            assertPw(usernameField).isVisible()
            assertThat(usernameField.inputValue()).isEqualTo(username)
        }
    }

    private fun assertLoginError(page: Page) {
        assertPw(
            page.getByText(
                "Incorrect login credentials. Please double check your username and password.",
                Page.GetByTextOptions().setExact(true)
            )
        ).isVisible()
    }

    private fun createLoginUser(enabled: Boolean) = userFactory.createUserWithRole(Role.MEMBER, enabled)
}
