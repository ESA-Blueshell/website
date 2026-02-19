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
import org.springframework.orm.ObjectOptimisticLockingFailureException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.function.Predicate
import kotlin.io.path.Path
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
    fun `join now opens membership signup page`() {
        withPage { page ->
            page.navigate("$frontendUrl/")

            val joinNowButton = page.getByRole(
                AriaRole.BUTTON,
                Page.GetByRoleOptions().setName("join now").setExact(false)
            )
            assertPw(joinNowButton).isVisible()
            joinNowButton.click()

            assertPw(page.getByText("MEMBERSHIP FORM", Page.GetByTextOptions().setExact(true))).isVisible()
            assertThat(page.url()).contains("/membership/signup")
        }
    }


    @Test
    fun `member users are redirected away from signup page`() {
        contributionFactory.createPeriod()

        val member = userFactory.createUserWithRole(Role.MEMBER, enabled = true)
        member.replaceMemberProfile(userFactory.buildMemberProfile(member))
        member.replaceAddress(userFactory.buildAddress(member))
        userRepository.saveAndFlush(member)
        userFactory.createMembership(member)

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, member.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            page.navigate("$frontendUrl/membership/signup")

            waitFor(
                timeoutMs = 8_000,
                onTimeoutMessage = { "Expected already-member flow to show snackbar message" }
            ) {
                page.getByText("you are already a member", Page.GetByTextOptions().setExact(false)).count() > 0
            }
        }
    }

    @Test
    fun `step 1 can create a user with personal info and continues to step 2`() {
        withPage { page ->
            page.navigate("$frontendUrl/membership/signup")

            // Assert that we are on step=1 using playwright
            waitFor(
                onTimeoutMessage = { "Expected to be on step 1 of membership signup after initial navigation" }
            ) {
                page.url().contains("/membership/signup") && !page.url().contains("step=")
            }

            val credentials = createAccountThroughUi(
                page = page,
                url = page.url(),
                submitButtonLabel = "Next",
                includeMemberProfile = true
            )

            page.waitForURL("**/membership/signup?step=2")

            assertThat(page.url()).contains("/membership/signup")
            assertThat(page.url()).contains("step=2")

            val persisted = waitForOptional(producer = { userRepository.findByUsername(credentials.username) })
            assertThat(persisted).isNotNull()

            assertThat(persisted.email).isEqualTo(credentials.email)
            assertThat(persisted.enabled).isFalse()
            assertThat(persisted.roles).contains(Role.GUEST)
            assertThat(persisted.memberProfile).isNotNull()

            assertEmailSent(credentials.email, "Activate your Account")
        }
    }

    @Test
    fun `step 2 can resend the activation email`() {
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
    fun `step 2 advances to step 3 when signing in through a separate window`() {
        withPage { page ->
            page.navigate("$frontendUrl/membership/signup")

            val credentials = createAccountThroughUi(
                page = page,
                url = page.url(),
                submitButtonLabel = "Next",
                includeMemberProfile = true
            )

            page.waitForURL("**/membership/signup?step=2")

            assertThat(page.url()).contains("/membership/signup")
            assertThat(page.url()).contains("step=2")

            val user = userRepository.findByUsername(credentials.username).get()
            user.enabled = true
            userRepository.saveAndFlush(user)

            // We sign in through a separate page, after which step two should detect the authenticated session and
            // advance to step three
            page.context().newPage().use { loginPage ->
                val loginStatus = AuthHelper.submitLogin(loginPage, frontendUrl, credentials.username, credentials.password)
                assertThat(loginStatus).isEqualTo(200)

                // Assert the login cookie has been set
                loginPage.waitForCondition {
                    loginPage.context().cookies(loginPage.url()).any { it.name == "login" }
                }
            }

            // Assert the login cookie is also set on the main page
            page.waitForCondition {
                page.context().cookies(page.url()).any { it.name == "login" }
            }

            page.waitForURL("**/membership/signup?step=3")

            assertThat(page.url()).contains("/membership/signup")
            assertThat(page.url()).contains("step=3")
        }
    }

    @Test
    fun `step 3 saves the address`() {
        contributionFactory.createPeriod()

        withPage { page ->
            val signupContext = createAndEnableMembershipSignupUser(page)
            val userId = signupContext.userId

            val loginStatus = AuthHelper.submitLogin(
                page,
                frontendUrl,
                signupContext.credentials.username,
                signupContext.credentials.password
            )
            assertThat(loginStatus).isEqualTo(200)

            page.navigate("$frontendUrl/membership/signup?step=3")

            waitFor(
                timeoutMs = 8_000,
                onTimeoutMessage = { "Expected membership flow to open at address step for logged-in user" }
            ) {
                page.url().contains("step=3")
            }

            fillAddressAndContinue(page)

            waitFor(
                timeoutMs = 10_000,
                onTimeoutMessage = { "Expected address to be persisted from membership step 3" }
            ) {
                userRepository.findById(userId).orElseThrow().address != null
            }
        }
    }

    @Test
    fun `step 4 completes a membership`() {
        contributionFactory.createPeriod()

        val seededUser = userFactory.createUserWithRole(Role.GUEST, enabled = true)
        seededUser.replaceMemberProfile(userFactory.buildMemberProfile(seededUser))
        seededUser.replaceAddress(userFactory.buildAddress(seededUser))
        val persistedUser = userRepository.saveAndFlush(seededUser)

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, persistedUser.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            page.navigate("$frontendUrl/membership/signup?step=4")
            assertStepFourVisible(page, "Expected fully pre-seeded user to access membership confirmation step")

            completeMembershipAtStepFour(page)
            assertMembershipPersisted(persistedUser.id!!)
        }
    }

    private fun createAndEnableMembershipSignupUser(page: Page): SignupContext {
        page.navigate("$frontendUrl/membership/signup")
        val credentials = createAccountThroughUi(
            page = page,
            url = page.url(),
            submitButtonLabel = "Next",
            includeMemberProfile = true
        )

        val createdUser = waitForOptional(producer = { userRepository.findByUsername(credentials.username) })
        val userId = enableUserByUsername(createdUser.username)

        return SignupContext(credentials = credentials, userId = userId)
    }

    private fun enableUserByUsername(username: String): Long {
        repeat(5) { attempt ->
            val user = waitForOptional(producer = { userRepository.findByUsername(username) })
            user.enabled = true
            try {
                return userRepository.saveAndFlush(user).id!!
            } catch (ex: ObjectOptimisticLockingFailureException) {
                if (attempt == 4) {
                    throw ex
                }
                Thread.sleep((attempt + 1) * 100L)
            }
        }
        throw IllegalStateException("Failed to enable user '$username' after retries")
    }

    private fun fillAddressAndContinue(page: Page) {
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
        val addressSaveResponse = page.waitForResponse(
            Predicate { response ->
                (response.request().method() == "POST" || response.request().method() == "PUT") &&
                        response.url().contains("/addresses")
            }
        ) {
            page.getByRole(
                AriaRole.BUTTON,
                Page.GetByRoleOptions().setName("Next").setExact(true)
            ).last().click()
        }
        assertThat(addressSaveResponse.status()).isBetween(200, 299)
    }

    private fun assertStepFourVisible(page: Page, onTimeoutMessage: String) {
        waitFor(
            timeoutMs = 8_000,
            onTimeoutMessage = { onTimeoutMessage }
        ) {
            page.url().contains("step=4")
        }

        assertPw(
            page.getByRole(
                AriaRole.CHECKBOX,
                Page.GetByRoleOptions().setName(
                    "I have understood and agree to the terms and conditions for membership listed above."
                )
            )
        ).isVisible()

        assertPw(
            page.getByRole(
                AriaRole.BUTTON,
                Page.GetByRoleOptions().setName("Complete").setExact(false)
            ).last()
        ).isVisible()
    }

    private fun completeMembershipAtStepFour(page: Page) {
        val consentLabel = "I have understood and agree to the terms and conditions for membership listed above."
        val consentCheckbox = page.getByRole(
            AriaRole.CHECKBOX,
            Page.GetByRoleOptions().setName(consentLabel).setExact(true)
        )
        assertPw(consentCheckbox).isVisible()
        if (!consentCheckbox.isChecked) {
            consentCheckbox.check()
        }
        waitFor(
            timeoutMs = 5_000,
            onTimeoutMessage = { "Expected membership consent checkbox to be checked before completion" }
        ) {
            consentCheckbox.isChecked
        }

        val membershipResponse = page.waitForResponse(
            Predicate { response ->
                response.request().method() == "POST" && response.url().contains("/memberships")
            }
        ) {
            clickCompleteMembership(page)
        }
        assertThat(membershipResponse.status()).isEqualTo(201)
    }

    private fun clickCompleteMembership(page: Page) {
        val clicked = page.evaluate(
            """() => {
                const button = document.querySelector('[data-testid="membership-complete-btn"]')
                if (!button) return false
                button.click()
                return true
            }"""
        ) as Boolean
        assertThat(clicked).isTrue()
    }

    private fun assertMembershipPersisted(userId: Long) {
        waitFor(
            timeoutMs = 10_000,
            onTimeoutMessage = { "Expected active membership to exist after completing step 4" }
        ) {
            membershipService.existsActiveMembershipByUserId(userId)
        }

        val refreshed = userRepository.findById(userId).orElseThrow()
        assertThat(refreshed.address).isNotNull()
        assertThat(refreshed.roles).contains(Role.MEMBER)
    }

    private data class SignupContext(
        val credentials: Credentials,
        val userId: Long,
    )

    private companion object {
        const val DEFAULT_PASSWORD = "Password123!"
    }
}
