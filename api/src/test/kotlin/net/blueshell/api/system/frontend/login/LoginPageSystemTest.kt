package net.blueshell.api.system.frontend.login

import com.microsoft.playwright.Page
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat as assertPw
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
