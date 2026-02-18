package net.blueshell.api.system.frontend.login

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import net.blueshell.api.domain.user.persistence.repository.UserRepository
import net.blueshell.api.platform.integration.mock.MockJavaMailSender
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.system.frontend.FrontendSystemTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@Tag("system")
class CreateAccountPageSystemTest @Autowired constructor(
    userRepository: UserRepository,
    mailSender: MockJavaMailSender,
) : FrontendSystemTestBase(
    userRepository = userRepository,
    mailSender = mailSender,
) {

    @Test
    fun `creates disabled account and sends activation email`() {
        withPage { page ->
            val credentials = createAccountThroughUi(
                page = page,
                url = "$frontendUrl/account/create",
                submitButtonLabel = "Create Account",
                includeMemberProfile = false
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
                includeMemberProfile = false
            )

            page.navigate("$frontendUrl/login")
            page.getByLabel("Username").fill(credentials.username)
            page.getByRole(
                AriaRole.TEXTBOX,
                Page.GetByRoleOptions().setName("Password")
            ).fill(credentials.password)
            page.getByRole(
                AriaRole.BUTTON,
                Page.GetByRoleOptions().setName("Login")
            ).click()

            assertThat(page.url()).contains("/login")

            val persisted = waitForOptional(producer = { userRepository.findByUsername(credentials.username) })
            assertThat(persisted.enabled).isFalse()
            assertEmailSent(credentials.email, "Activate your Account")
        }
    }
}
