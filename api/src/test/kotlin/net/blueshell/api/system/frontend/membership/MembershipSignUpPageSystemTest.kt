package net.blueshell.api.system.frontend.membership

import com.microsoft.playwright.Page
import com.microsoft.playwright.Locator
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
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.function.Predicate
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
    fun `step two sign in returns to membership signup and advances to address`() {
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

            page.getByRole(
                AriaRole.BUTTON,
                Page.GetByRoleOptions().setName("Sign in").setExact(false)
            ).click()

            waitFor(
                timeoutMs = 12_000,
                onTimeoutMessage = { "Expected to be on login page with membership redirect query" }
            ) {
                page.url().contains("/login") && page.url().contains("redirect=")
            }
            val decodedLoginUrl = URLDecoder.decode(page.url(), StandardCharsets.UTF_8)
            assertThat(decodedLoginUrl).contains("redirect=/membership/signup?step=2")

            val authResponse = page.waitForResponse("**/auth") {
                page.getByLabel("Username").fill(credentials.username)
                page.getByRole(
                    AriaRole.TEXTBOX,
                    Page.GetByRoleOptions().setName("Password")
                ).fill(credentials.password)
                page.getByRole(
                    AriaRole.BUTTON,
                    Page.GetByRoleOptions().setName("Login")
                ).click()
            }
            assertThat(authResponse.status()).isEqualTo(200)

            waitFor(
                timeoutMs = 12_000,
                onTimeoutMessage = { "Expected successful login redirect to membership address step" }
            ) {
                page.url().contains("/membership/signup") && page.url().contains("step=3")
            }
        }
    }

    @Test
    fun `step three saves address for logged-in user`() {
        contributionFactory.createPeriod()

        withPage { page ->
            val signupContext = createAndEnableMembershipSignupUser(page)
            val userId = signupContext.userId

            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, signupContext.credentials.username, signupContext.credentials.password)
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
    fun `step four completes membership with address pre-seeded after UI signup`() {
        contributionFactory.createPeriod()

        withPage { page ->
            val signupContext = createAndEnableMembershipSignupUser(page)
            val userId = signupContext.userId
            seedAddress(userId)

            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, signupContext.credentials.username, signupContext.credentials.password)
            assertThat(loginStatus).isEqualTo(200)

            page.navigate("$frontendUrl/membership/signup?step=4")
            assertStepFourVisible(page, "Expected eligible user to access membership confirmation step")

            completeMembershipAtStepFour(page)
            assertMembershipPersisted(userId)
        }
    }

    @Test
    fun `step four completes membership with fully pre-seeded user`() {
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

    private fun createAndEnableMembershipSignupUser(page: Page): SignupContext {
        page.navigate("$frontendUrl/membership/signup")
        val credentials = createAccountThroughUi(
            page = page,
            url = page.url(),
            submitButtonLabel = "Next",
            includeMemberProfile = true
        )

        val createdUser = waitForOptional(producer = { userRepository.findByUsername(credentials.username) })
        createdUser.enabled = true
        val persistedUser = userRepository.saveAndFlush(createdUser)

        return SignupContext(credentials = credentials, userId = persistedUser.id!!)
    }

    private fun seedAddress(userId: Long) {
        val user = userRepository.findById(userId).orElseThrow()
        if (user.address == null) {
            user.replaceAddress(userFactory.buildAddress(user))
            userRepository.saveAndFlush(user)
        }
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
        val consentReady = page.evaluate(
            """() => {
                const consentText = "I have understood and agree to the terms and conditions for membership listed above."
                const visibleButton = Array.from(document.querySelectorAll('button'))
                  .find((element) => element.textContent?.includes('Complete Membership') && element.offsetParent !== null)
                if (!visibleButton) return false

                const card = visibleButton.closest('.v-card') || document
                const checkbox = card.querySelector(
                  'input[aria-label=\"I have understood and agree to the terms and conditions for membership listed above.\"]'
                )
                if (!checkbox) return false

                if (!checkbox.checked) {
                  const label = Array.from(card.querySelectorAll('label'))
                    .find((element) => element.textContent?.includes(consentText) && element.offsetParent !== null)
                  label?.click()
                }

                if (!checkbox.checked) {
                  checkbox.click()
                }

                checkbox.dispatchEvent(new Event('input', { bubbles: true }))
                checkbox.dispatchEvent(new Event('change', { bubbles: true }))
                return checkbox.checked
            }"""
        ) as Boolean
        assertThat(consentReady).isTrue()

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
                const visibleButton = Array.from(document.querySelectorAll('button'))
                    .find((element) => element.textContent?.includes('Complete Membership') && element.offsetParent !== null)
                if (!visibleButton) return false
                visibleButton.click()
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

    private fun eventually(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(200)
        }
        return false
    }

    private data class SignupContext(
        val credentials: Credentials,
        val userId: Long,
    )

    private companion object {
        const val DEFAULT_PASSWORD = "Password123!"
    }
}
