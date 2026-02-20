package net.blueshell.api.system.frontend.login

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import net.blueshell.api.domain.auth.application.factory.RecoveryTokenFactory
import net.blueshell.api.factory.user.persistence.UserFactory
import net.blueshell.api.shared.enums.ResetType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.system.frontend.FrontendSystemTestBase
import net.blueshell.api.system.frontend.helper.AuthHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration

@Tag("system")
class ActivationPageSystemTest : FrontendSystemTestBase() {

    @Autowired
    private lateinit var userFactory: UserFactory

    @Autowired
    private lateinit var recoveryTokenFactory: RecoveryTokenFactory

    @Test
    fun `member activation activates account and allows sign in`() {
        val user = userFactory.createUserWithRole(Role.MEMBER, enabled = false)
        val rawToken = recoveryTokenFactory.issue(
            user = user,
            type = ResetType.MEMBER_ACTIVATION,
            ttl = Duration.ofDays(7)
        )
        val encodedToken = URLEncoder.encode(rawToken, StandardCharsets.UTF_8)
        val newUsername = "member${System.currentTimeMillis().toString().takeLast(8)}"
        val newPassword = "N3wMemberPass!"

        withPage { page ->
            page.navigate("$frontendUrl/account/activate/member?token=$encodedToken")
            submitMemberActivationForm(page, username = newUsername, password = newPassword)

            val response = page.waitForResponse("**/recovery/member/activate") {
                page.getByRole(
                    AriaRole.BUTTON,
                    Page.GetByRoleOptions().setName("Activate Member").setExact(true)
                ).click()
            }
            assertThat(response.status()).isEqualTo(200)
            assertThat(
                page.getByText("Account activated! You will be redirected to the login page.").count()
            ).isGreaterThan(0)
        }

        waitFor(
            onTimeoutMessage = { "Expected user ${user.id} to be enabled after member activation" }
        ) {
            userRepository.findById(user.id!!).orElseThrow().enabled
        }

        withPage { page ->
            val status = AuthHelper.submitLogin(page, frontendUrl, newUsername, newPassword)
            assertThat(status).isEqualTo(200)
        }
    }

    @Test
    fun `member activation shows error for invalid token`() {
        val user = userFactory.createUserWithRole(Role.MEMBER, enabled = false)
        val invalidToken = URLEncoder.encode("invalid-member-token", StandardCharsets.UTF_8)
        val newUsername = "member${System.currentTimeMillis().toString().takeLast(8)}"
        val newPassword = "N3wMemberPass!"

        withPage { page ->
            page.navigate("$frontendUrl/account/activate/member?token=$invalidToken")
            submitMemberActivationForm(page, username = newUsername, password = newPassword)

            val response = page.waitForResponse("**/recovery/member/activate") {
                page.getByRole(
                    AriaRole.BUTTON,
                    Page.GetByRoleOptions().setName("Activate Member").setExact(true)
                ).click()
            }
            assertThat(response.status()).isGreaterThanOrEqualTo(400)
            assertThat(
                page.getByText("We couldn’t activate your membership. The link may be invalid or expired.").count()
            ).isGreaterThan(0)
        }

        assertThat(userRepository.findById(user.id!!).orElseThrow().enabled).isFalse()
    }

    @Test
    fun `user activation enables account and returns membership step redirect path`() {
        val user = userFactory.createUserWithRole(Role.MEMBER, enabled = false)
        user.replaceMemberProfile(userFactory.buildMemberProfile(user))
        userRepository.saveAndFlush(user)
        val rawToken = recoveryTokenFactory.issue(
            user = user,
            type = ResetType.USER_ACTIVATION,
            ttl = Duration.ofHours(1)
        )
        val encodedToken = URLEncoder.encode(rawToken, StandardCharsets.UTF_8)

        withPage { page ->
            val response = page.waitForResponse("**/recovery/user/activate") {
                page.navigate("$frontendUrl/account/activate/user?token=$encodedToken")
            }
            assertThat(response.status()).isEqualTo(200)
            assertThat(response.text()).contains("/membership/signUp?step=2")
            assertThat(
                page.getByText("Account activated! You will be redirected to the login page.").count()
            ).isGreaterThan(0)
        }

        waitFor(
            onTimeoutMessage = { "Expected user ${user.id} to be enabled after user activation" }
        ) {
            userRepository.findById(user.id!!).orElseThrow().enabled
        }
    }

    @Test
    fun `user activation with invalid token shows warning`() {
        val user = userFactory.createUserWithRole(Role.GUEST, enabled = false)
        val invalidToken = URLEncoder.encode("invalid-user-token", StandardCharsets.UTF_8)

        withPage { page ->
            val response = page.waitForResponse("**/recovery/user/activate") {
                page.navigate("$frontendUrl/account/activate/user?token=$invalidToken")
            }
            assertThat(response.status()).isGreaterThanOrEqualTo(400)
            assertThat(
                page.getByText("You will be redirected to the login page.", Page.GetByTextOptions().setExact(false)).count()
            ).isGreaterThan(0)
        }

        assertThat(userRepository.findById(user.id!!).orElseThrow().enabled).isFalse()
    }

    private fun submitMemberActivationForm(page: Page, username: String, password: String) {
        page.getByLabel("Username").fill(username)
        page.getByLabel("Password").first().fill(password)
        page.getByLabel("Repeat Password").first().fill(password)
        page.getByLabel("Repeat Password").first().press("Tab")
    }
}
