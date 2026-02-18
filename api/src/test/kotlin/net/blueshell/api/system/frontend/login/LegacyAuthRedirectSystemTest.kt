package net.blueshell.api.system.frontend.login

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import net.blueshell.api.system.frontend.FrontendSystemTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("system")
class LegacyAuthRedirectSystemTest : FrontendSystemTestBase() {

    @Test
    fun `legacy reset password route redirects to query path`() {
        val username = "legacy-user"
        val token = "legacy-reset-token"

        withPage { page ->
            page.navigate("$frontendUrl/account/reset-password/$username/$token")
            page.waitForURL("**/account/reset-password?**")

            assertThat(page.url()).contains("/account/reset-password")
            assertThat(page.url()).contains("username=$username")
            assertThat(page.url()).contains("token=$token")
        }
    }

    @Test
    fun `legacy member activation route redirects to query path`() {
        val token = "legacy-member-token"

        withPage { page ->
            page.navigate("$frontendUrl/account/activate/member/$token")
            page.waitForURL("**/account/activate/member?**")

            assertThat(page.url()).contains("/account/activate/member")
            assertThat(page.url()).contains("token=$token")
            assertThat(
                page.getByRole(
                    AriaRole.TEXTBOX,
                    Page.GetByRoleOptions().setName("Reset token")
                ).inputValue()
            ).isEqualTo(token)
        }
    }

    @Test
    fun `legacy user activation route redirects to query path`() {
        val username = "legacy-user"
        val token = "legacy-user-token"

        withPage { page ->
            page.navigate("$frontendUrl/account/activate/user/$username/$token")
            page.waitForURL("**/account/activate/user?**")

            assertThat(page.url()).contains("/account/activate/user")
            assertThat(page.url()).contains("username=$username")
            assertThat(page.url()).contains("token=$token")
        }
    }
}
