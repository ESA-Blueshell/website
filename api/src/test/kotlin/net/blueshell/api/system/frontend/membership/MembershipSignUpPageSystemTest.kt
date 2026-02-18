package net.blueshell.api.system.frontend.membership

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.system.frontend.FrontendSystemTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat as assertPw

@Tag("system")
class MembershipSignUpPageSystemTest : FrontendSystemTestBase() {

    @Test
    fun `join now opens signup and creates disabled account`() {
        withPage { page ->
            page.navigate("$frontendUrl/")

            val joinNowButton = page.getByRole(
                AriaRole.BUTTON,
                Page.GetByRoleOptions().setName("join now").setExact(false)
            )
            // Assert that the join now button is present and click it
            assertPw(joinNowButton).isVisible()

            joinNowButton.click()

            assertPw(page.getByText("MEMBERSHIP FORM", Page.GetByTextOptions().setExact(true))).isVisible()

            assertThat(page.url()).contains("/membership/signup")

            val credentials = createAccountThroughUi(
                page = page,
                url = page.url(),
                submitButtonLabel = "Next",
                includeMemberProfile = true
            )

            val persisted = waitForOptional(producer = { userRepository.findByUsername(credentials.username) })
            assertThat(persisted.email).isEqualTo(credentials.email)
            assertThat(persisted.enabled).isFalse()
            assertThat(persisted.roles).contains(Role.GUEST)
            assertThat(persisted.memberProfile).isNotNull()

            assertEmailSent(credentials.email, "Activate your Account")
        }
    }
}
