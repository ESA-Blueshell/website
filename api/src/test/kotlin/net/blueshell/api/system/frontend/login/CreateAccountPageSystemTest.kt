package net.blueshell.api.system.frontend.login

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.system.frontend.FrontendSystemTestBase
import net.blueshell.api.system.frontend.helper.AuthHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("system")
class CreateAccountPageSystemTest : FrontendSystemTestBase() {

    @Test
    fun `creates disabled account and sends activation email`() {
        withPage { page ->
            val credentials = createAccountThroughUi(
                page = page,
                url = "$frontendUrl/account/create",
                submitButtonLabel = "Create Account",
                includeMemberProfile = false,
                submitButtonTestId = "user-form-submit-btn"
            )

            val persisted = waitForOptional(producer = { userRepository.findByUsername(credentials.username) })
            assertThat(persisted.email).isEqualTo(credentials.email)
            assertThat(persisted.enabled).isFalse()
            assertThat(persisted.roles).contains(Role.GUEST)
            assertThat(persisted.password).isNotEqualTo(credentials.password)

            assertEmailSent(credentials.email, "Activate your Account")
        }
    }

    @Test
    fun `blocks login before activation`() {
        withPage { page ->
            val credentials = createAccountThroughUi(
                page = page,
                url = "$frontendUrl/account/create",
                submitButtonLabel = "Create Account",
                includeMemberProfile = false,
                submitButtonTestId = "user-form-submit-btn"
            )

            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, credentials.username, credentials.password)

            assertThat(page.url()).contains("/login")
            assertThat(loginStatus).isEqualTo(401)

            val persisted = waitForOptional(producer = { userRepository.findByUsername(credentials.username) })
            assertThat(persisted.enabled).isFalse()
            assertEmailSent(credentials.email, "Activate your Account")
        }
    }
}
