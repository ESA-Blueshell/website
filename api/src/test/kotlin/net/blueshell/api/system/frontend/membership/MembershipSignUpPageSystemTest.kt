package net.blueshell.api.system.frontend.membership

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat as assertPw
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.system.frontend.FrontendSystemTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("system")
class MembershipSignUpPageSystemTest : FrontendSystemTestBase() {

    @Test
    fun `home join now navigates to membership signup and allows account creation with activation email`() {
        withPage { page ->
            page.navigate("$frontendBaseUrl/")
            page.getByRole(
                com.microsoft.playwright.options.AriaRole.BUTTON,
                com.microsoft.playwright.Page.GetByRoleOptions().setName("join now").setExact(false)
            ).click()

            assertThat(page.url()).contains("/membership/signup")

            val credentials = createAccountThroughUi(
                page = page,
                url = page.url(),
                submitButtonLabel = "Next",
                includeMemberProfile = true
            )

            assertPw(page.getByText("Check your inbox")).isVisible()
            assertPw(page.getByText(credentials.email)).isVisible()

            val persisted = waitForUserByUsername(credentials.username)
            assertThat(persisted.email).isEqualTo(credentials.email)
            assertThat(persisted.enabled).isFalse()
            assertThat(persisted.roles).contains(Role.GUEST)
            assertThat(persisted.memberProfile).isNotNull()

            assertActivationEmailSent(credentials.email)
        }
    }
}
