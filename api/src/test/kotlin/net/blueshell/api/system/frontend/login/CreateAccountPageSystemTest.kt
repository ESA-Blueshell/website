package net.blueshell.api.system.frontend.login

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat as assertPw
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.system.frontend.FrontendSystemTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("system")
class CreateAccountPageSystemTest : FrontendSystemTestBase() {

    @Test
    fun `account create page creates disabled user and sends activation email`() {
        withPage { page ->
            val credentials = createAccountThroughUi(
                page = page,
                url = "$frontendBaseUrl/account/create",
                submitButtonLabel = "Create Account",
                includeMemberProfile = false
            )

            assertPw(page.getByText("Your account has successfully been created!"))
                .isVisible()

            val persisted = waitForUserByUsername(credentials.username)
            assertThat(persisted.email).isEqualTo(credentials.email)
            assertThat(persisted.enabled).isFalse()
            assertThat(persisted.roles).contains(Role.GUEST)
            assertThat(persisted.password).isNotEqualTo(credentials.password)

            assertActivationEmailSent(credentials.email)
        }
    }

    @Test
    fun `account create page user cannot sign in before activation`() {
        withPage { page ->
            val credentials = createAccountThroughUi(
                page = page,
                url = "$frontendBaseUrl/account/create",
                submitButtonLabel = "Create Account",
                includeMemberProfile = false
            )

            page.navigate("$frontendBaseUrl/login")
            page.getByLabel("Username").fill(credentials.username)
            page.getByLabel("Password", com.microsoft.playwright.Page.GetByLabelOptions().setExact(false))
                .fill(credentials.password)
            page.getByRole(
                com.microsoft.playwright.options.AriaRole.BUTTON,
                com.microsoft.playwright.Page.GetByRoleOptions().setName("Login")
            ).click()

            assertPw(page.getByText("Incorrect login credentials. Please double check your username and password."))
                .isVisible()

            val persisted = waitForUserByUsername(credentials.username)
            assertThat(persisted.enabled).isFalse()
            assertActivationEmailSent(credentials.email)
        }
    }
}
