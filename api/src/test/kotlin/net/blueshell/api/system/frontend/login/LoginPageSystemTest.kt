package net.blueshell.api.system.frontend.login

import com.microsoft.playwright.Page
import com.microsoft.playwright.Response
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat as assertPw
import com.microsoft.playwright.options.AriaRole
import net.blueshell.api.system.frontend.FrontendSystemTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder

@Tag("system")
class LoginPageSystemTest : FrontendSystemTestBase() {
    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Test
    fun `disabled account shows login error`() {
        withPage { page ->
            val credentials = createAccountThroughUi(
                page = page,
                url = "$frontendUrl/account/create",
                submitButtonLabel = "Create Account",
                includeMemberProfile = false
            )

            val persisted = waitForOptional(producer = { userRepository.findByUsername(credentials.username) })
            assertThat(persisted.enabled).isFalse()

            val status = submitLogin(page, credentials.username, credentials.password)
            assertThat(status).isEqualTo(401)
            assertLoginError(page)
        }
    }

    @Test
    fun `wrong password shows login error`() {
        withPage { page ->
            val credentials = createAccountThroughUi(
                page = page,
                url = "$frontendUrl/account/create",
                submitButtonLabel = "Create Account",
                includeMemberProfile = false
            )

            val user = waitForOptional(producer = { userRepository.findByUsername(credentials.username) })
            user.enabled = true
            userRepository.save(user)

            val status = submitLogin(page, credentials.username, "${credentials.password}x")
            assertThat(status).isEqualTo(401)
            assertLoginError(page)
        }
    }

    @Test
    fun `enabled account logs in with correct password`() {
        withPage { page ->
            val credentials = createAccountThroughUi(
                page = page,
                url = "$frontendUrl/account/create",
                submitButtonLabel = "Create Account",
                includeMemberProfile = false
            )
            val loginPassword = "Passw0rd!Aa"
            val user = waitForOptional(producer = { userRepository.findByUsername(credentials.username) })
            user.enabled = true
            user.password = passwordEncoder.encode(loginPassword)
            userRepository.save(user)

            val status = submitLogin(page, credentials.username, loginPassword)
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

    private fun submitLogin(page: Page, username: String, password: String): Int {
        page.navigate("$frontendUrl/login/")
        page.getByLabel("Username").fill(username)
        page.getByRole(
            AriaRole.TEXTBOX,
            Page.GetByRoleOptions().setName("Password")
        ).fill(password)

        val response: Response = page.waitForResponse("**/auth") {
            page.getByRole(
                AriaRole.BUTTON,
                Page.GetByRoleOptions().setName("Login")
            ).click()
        }
        return response.status()
    }

    private fun assertLoginError(page: Page) {
        assertPw(
            page.getByText(
                "Incorrect login credentials. Please double check your username and password.",
                Page.GetByTextOptions().setExact(true)
            )
        ).isVisible()
    }
}
