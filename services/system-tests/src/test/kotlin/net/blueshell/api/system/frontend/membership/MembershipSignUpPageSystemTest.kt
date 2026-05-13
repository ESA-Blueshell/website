package net.blueshell.api.system.frontend.membership

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import net.blueshell.api.ApiApplication
import net.blueshell.api.config.TestCleanUpListener
import net.blueshell.api.system.frontend.helper.AddressFormHelper
import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.api.system.frontend.helper.MembershipSignUpHelper
import net.blueshell.api.system.frontend.helper.UserFormHelper
import net.blueshell.systemtests.PlaywrightTestBase
import net.blueshell.systemtests.TestHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestExecutionListeners
import java.time.LocalDate
import java.util.function.Predicate
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat as assertPw

@Tag("system")
@ActiveProfiles("test")
@TestExecutionListeners(
    listeners = [TestCleanUpListener::class],
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS,
)
@SpringBootTest(
    classes = [ApiApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties = ["server.port=8080", "app.jobs.auto-dispatch=true"],
)
class MembershipSignUpPageSystemTest : PlaywrightTestBase() {

    @Test
    fun `step 1 can create a user with personal info and continues to step 2`() {
        MembershipSignUpHelper.open(page, frontendUrl)

        pollFor("step 1 of membership signup after initial navigation") {
            page.url().contains("/membership/signup") && !page.url().contains("step=")
        }

        val credentials = createAccountThroughUi(page)

        page.waitForURL("**/membership/signup?step=2")
        assertThat(page.url()).contains("/membership/signup")
        assertThat(page.url()).contains("step=2")

        val persisted = pollForUser(credentials.username)
        assertThat(persisted.email).isEqualTo(credentials.email)
        assertThat(persisted.enabled).isFalse()
        assertThat(TestHelper.findRoles(credentials.username)).contains("GUEST")
        assertThat(TestHelper.hasMemberProfile(persisted.id)).isTrue()

        TestHelper.assertEmailSent(credentials.email, "Activate your Account")
    }

    @Test
    fun `step 2 can resend the activation email`() {
        MembershipSignUpHelper.open(page, frontendUrl)

        val credentials = createAccountThroughUi(page)

        val resendResponse = page.waitForResponse("**/recovery/user/activate/resend/**") {
            MembershipSignUpHelper.clickStepTwoResend(page)
        }
        assertThat(resendResponse.status()).isEqualTo(204)

        val persisted = pollForUser(credentials.username)
        assertThat(persisted.enabled).isFalse()
    }

    @Test
    fun `step 3 saves the address`() {
        TestHelper.createContributionPeriod(
            LocalDate.now().minusDays(15),
            LocalDate.now().plusDays(345),
        )

        MembershipSignUpHelper.open(page, frontendUrl)
        val credentials = createAccountThroughUi(page)
        val user = pollForUser(credentials.username)
        TestHelper.setEnabled(user.username, true)

        val loginStatus = AuthHelper.submitLogin(
            page,
            frontendUrl,
            credentials.username,
            credentials.password,
        )
        assertThat(loginStatus).isEqualTo(200)

        page.navigate("$frontendUrl/membership/signup?step=3")

        pollFor("membership flow opens at address step for logged-in user", timeoutMs = 8_000) {
            page.url().contains("step=3")
        }

        fillAddressAndContinue(page)

        pollFor("address persisted from membership step 3", timeoutMs = 10_000) {
            TestHelper.findAddress(user.username) != null
        }
    }

    @Test
    fun `step 4 completes a membership`() {
        TestHelper.createContributionPeriod(
            LocalDate.now().minusDays(15),
            LocalDate.now().plusDays(345),
        )

        val seededUser = TestHelper.registerActivateAndPromote("GUEST")
        TestHelper.attachMemberProfile(seededUser)
        TestHelper.attachAddress(
            user = seededUser,
            city = "Enschede",
            street = "System Test Street",
            houseNumber = "42A",
            zipCode = "7521AB",
        )
        val seededId = TestHelper.findUser(seededUser.username)!!.id

        val loginStatus = AuthHelper.submitLogin(
            page,
            frontendUrl,
            seededUser.username,
            seededUser.password,
        )
        assertThat(loginStatus).isEqualTo(200)

        page.navigate("$frontendUrl/membership/signup?step=4")
        assertStepFourVisible(page)

        completeMembershipAtStepFour(page)
        assertMembershipPersisted(seededUser.username, seededId)
    }

    private data class Credentials(val username: String, val email: String, val password: String)

    private fun createAccountThroughUi(page: Page): Credentials {
        val suffix = System.currentTimeMillis().toString().takeLast(8)
        val username = "sysuser$suffix"
        val email = "sysuser$suffix@example.com"
        val password = "Passw0rd!$suffix"
        val phoneNumber = "+3161${suffix.takeLast(7)}"

        UserFormHelper.fill(
            page = page,
            fields = UserFormHelper.Fields(
                initials = "SU",
                firstName = "System",
                surname = "User$suffix",
                username = username,
                discord = "sysuser$suffix",
                email = email,
                phoneNumber = phoneNumber,
                password = password,
                repeatedPassword = password,
                dateOfBirth = "1999-04-12",
                gender = "X",
                studentNumber = "s$suffix",
            ),
        )

        if (UserFormHelper.acceptPrivacyConsentIfVisible(page)) {
            pollFor("privacy consent checkbox checked", timeoutMs = 5_000) {
                UserFormHelper.privacyConsentCheckbox(page).isChecked
            }
        }

        page.waitForResponse(
            { response ->
                response.request().method() == "POST" && response.url().contains("/users")
            },
        ) {
            page.locator("[data-testid='membership-step1-next-btn']").first().click()
        }

        return Credentials(username, email, password)
    }

    private fun fillAddressAndContinue(page: Page) {
        val suffix = System.currentTimeMillis().toString().takeLast(8)
        AddressFormHelper.fill(
            page,
            AddressFormHelper.Fields(
                street = "System Test Street",
                houseNumber = "42A",
                zipCode = "7521AB",
                city = "Enschede$suffix",
            ),
        )
        val addressSaveResponse = page.waitForResponse(
            Predicate { response ->
                (response.request().method() == "POST" || response.request().method() == "PUT") &&
                    response.url().contains("/addresses")
            },
        ) {
            MembershipSignUpHelper.clickStepThreeNext(page)
        }
        assertThat(addressSaveResponse.status()).isBetween(200, 299)
    }

    private fun assertStepFourVisible(page: Page) {
        pollFor("membership step 4 visible for pre-seeded user", timeoutMs = 8_000) {
            page.url().contains("step=4")
        }

        assertPw(membershipConsentCheckbox(page)).isVisible()
        assertPw(MembershipSignUpHelper.stepFourCompleteButton(page)).isVisible()
    }

    private fun completeMembershipAtStepFour(page: Page) {
        val consentCheckbox = membershipConsentCheckbox(page)
        assertPw(consentCheckbox).isVisible()
        if (!consentCheckbox.isChecked) {
            consentCheckbox.check()
        }
        pollFor("membership consent checkbox checked before completion", timeoutMs = 5_000) {
            consentCheckbox.isChecked
        }

        val membershipResponse = page.waitForResponse(
            Predicate { response ->
                response.request().method() == "POST" && response.url().contains("/memberships")
            },
        ) {
            MembershipSignUpHelper.clickStepFourComplete(page)
        }
        assertThat(membershipResponse.status()).isEqualTo(201)
    }

    private fun membershipConsentCheckbox(page: Page) = page.getByRole(
        AriaRole.CHECKBOX,
        Page.GetByRoleOptions()
            .setName(MEMBERSHIP_CONSENT_LABEL_PREFIX)
            .setExact(false),
    )

    private fun assertMembershipPersisted(username: String, userId: Long) {
        pollFor("active membership exists after completing step 4", timeoutMs = 10_000) {
            TestHelper.hasActiveMembership(username)
        }
        assertThat(TestHelper.findAddress(username)).isNotNull()
        assertThat(TestHelper.findRoles(username)).contains("MEMBER")
        assertThat(TestHelper.hasMemberProfile(userId)).isTrue()
    }

    private fun pollForUser(username: String): TestHelper.RegisteredUserRow {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            val row = TestHelper.findUser(username)
            if (row != null) return row
            Thread.sleep(200)
        }
        throw AssertionError("Expected user '$username' to be persisted within 10s")
    }

    private fun pollFor(description: String, timeoutMs: Long = 6_000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            Thread.sleep(200)
        }
        throw AssertionError("Expected $description within ${timeoutMs}ms")
    }

    private companion object {
        const val MEMBERSHIP_CONSENT_LABEL_PREFIX = "I confirm that I have read and agree to the membership terms above"
    }
}
