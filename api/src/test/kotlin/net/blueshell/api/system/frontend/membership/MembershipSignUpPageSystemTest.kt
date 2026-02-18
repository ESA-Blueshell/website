package net.blueshell.api.system.frontend.membership

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import net.blueshell.api.domain.user.persistence.repository.UserRepository
import net.blueshell.api.platform.integration.job.repository.JobExecutionRepository
import net.blueshell.api.platform.integration.mock.MockJavaMailSender
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.system.frontend.FrontendSystemTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat as assertPw

@Tag("system")
class MembershipSignUpPageSystemTest @Autowired constructor(
    userRepository: UserRepository,
    mailSender: MockJavaMailSender,
    jobExecutionRepository: JobExecutionRepository,
) : FrontendSystemTestBase(
    userRepository = userRepository,
    mailSender = mailSender,
    jobExecutionRepository = jobExecutionRepository
) {

    @Test
    fun `home join now navigates to membership signup and allows account creation with activation email`() {
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

            assertPw(page.getByText("Check your inbox")).isVisible()
            assertPw(page.getByText(credentials.email)).isVisible()

            val persisted = waitForOptional(producer = { userRepository.findByUsername(credentials.username) })
            assertThat(persisted.email).isEqualTo(credentials.email)
            assertThat(persisted.enabled).isFalse()
            assertThat(persisted.roles).contains(Role.GUEST)
            assertThat(persisted.memberProfile).isNotNull()

            assertActivationEmailSent(credentials.email)
        }
    }
}
