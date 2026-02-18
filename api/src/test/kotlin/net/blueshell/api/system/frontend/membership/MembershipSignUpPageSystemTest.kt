package net.blueshell.api.system.frontend.membership

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import net.blueshell.api.domain.user.application.MembershipService
import net.blueshell.api.factory.contribution.persistence.ContributionFactory
import net.blueshell.api.factory.user.persistence.UserFactory
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.system.frontend.FrontendSystemTestBase
import net.blueshell.api.system.frontend.helper.AddressFormHelper
import net.blueshell.api.system.frontend.helper.AuthHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat as assertPw

@Tag("system")
class MembershipSignUpPageSystemTest : FrontendSystemTestBase() {

    @Autowired
    private lateinit var userFactory: UserFactory

    @Autowired
    private lateinit var membershipService: MembershipService

    @Autowired
    private lateinit var contributionFactory: ContributionFactory

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

    @Test
    fun `step two can resend activation email`() {
        withPage { page ->
            page.navigate("$frontendUrl/membership/signup")

            val credentials = createAccountThroughUi(
                page = page,
                url = page.url(),
                submitButtonLabel = "Next",
                includeMemberProfile = true
            )

            val resendResponse = page.waitForResponse("**/recovery/user/activate/resend/**") {
                page.getByRole(
                    AriaRole.BUTTON,
                    Page.GetByRoleOptions().setName("Resend email").setExact(false)
                ).click()
            }
            assertThat(resendResponse.status()).isEqualTo(204)

            val persisted = waitForOptional(producer = { userRepository.findByUsername(credentials.username) })
            assertThat(persisted.enabled).isFalse()
        }
    }

    @Test
    fun `membership flow completes address and membership after login`() {
        contributionFactory.createPeriod()

        withPage { page ->
            page.navigate("$frontendUrl/membership/signup")

            val credentials = createAccountThroughUi(
                page = page,
                url = page.url(),
                submitButtonLabel = "Next",
                includeMemberProfile = true
            )

            val createdUser = waitForOptional(producer = { userRepository.findByUsername(credentials.username) })
            createdUser.enabled = true
            userRepository.saveAndFlush(createdUser)

            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, credentials.username, credentials.password)
            assertThat(loginStatus).isEqualTo(200)

            page.navigate("$frontendUrl/membership/signup?step=3")

            waitFor(
                timeoutMs = 8_000,
                onTimeoutMessage = { "Expected membership flow to open at address step for logged-in user" }
            ) {
                page.url().contains("step=3")
            }

            val suffix = System.currentTimeMillis().toString().takeLast(8)
            AddressFormHelper.fill(
                page,
                AddressFormHelper.Fields(
                    street = "System Test Street",
                    houseNumber = "42A",
                    zipCode = "7521AB",
                    city = "Enschede$suffix"
                )
            )
            page.getByRole(
                AriaRole.BUTTON,
                Page.GetByRoleOptions().setName("Next").setExact(true)
            ).last().click()

            waitFor(
                timeoutMs = 8_000,
                onTimeoutMessage = { "Expected membership flow to continue at membership confirmation step" }
            ) {
                page.url().contains("step=4")
            }

            page.getByRole(
                AriaRole.CHECKBOX,
                Page.GetByRoleOptions().setName("I have understood and agree to the terms and conditions for membership listed above.")
            ).click()
            page.getByRole(
                AriaRole.BUTTON,
                Page.GetByRoleOptions().setName("Complete Membership").setExact(true)
            ).click()

            assertPw(page.getByText("Membership Complete!", Page.GetByTextOptions().setExact(true))).isVisible()

            waitFor(
                timeoutMs = 8_000,
                onTimeoutMessage = { "Expected active membership to exist after completing step 4" }
            ) {
                membershipService.existsActiveMembershipByUserId(createdUser.id!!)
            }

            val refreshed = userRepository.findById(createdUser.id!!).orElseThrow()
            assertThat(refreshed.address).isNotNull()
            assertThat(refreshed.roles).contains(Role.MEMBER)
        }
    }

    @Test
    fun `already-member user is redirected away from signup with snackbar`() {
        contributionFactory.createPeriod()

        val member = userFactory.createUserWithRole(Role.MEMBER, enabled = true)
        member.replaceMemberProfile(userFactory.buildMemberProfile(member))
        member.replaceAddress(userFactory.buildAddress(member))
        userRepository.saveAndFlush(member)
        userFactory.createMembership(member)

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, member.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            page.navigate("$frontendUrl/membership/signup?step=4")

            waitFor(
                timeoutMs = 8_000,
                onTimeoutMessage = { "Expected already-member flow to show snackbar message" }
            ) {
                page.getByText("you are already a member", Page.GetByTextOptions().setExact(false)).count() > 0
            }

            waitFor(
                timeoutMs = 12_000,
                onTimeoutMessage = { "Expected already-member snackbar to remain visible long enough for user feedback" }
            ) {
                page.getByText("you are already a member", Page.GetByTextOptions().setExact(false)).count() > 0
            }
        }
    }

    private companion object {
        const val DEFAULT_PASSWORD = "Password123!"
    }
}
