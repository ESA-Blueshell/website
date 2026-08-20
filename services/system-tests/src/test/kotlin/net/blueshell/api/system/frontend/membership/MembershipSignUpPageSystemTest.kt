package net.blueshell.api.system.frontend.membership

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import net.blueshell.api.system.frontend.helper.AddressFormHelper
import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.api.system.frontend.helper.MembershipSignUpHelper
import net.blueshell.api.system.frontend.helper.UserFormHelper
import net.blueshell.systemtests.PlaywrightTestBase
import net.blueshell.systemtests.TestHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.function.Predicate
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat as assertPw

@Tag("system")
class MembershipSignUpPageSystemTest : PlaywrightTestBase() {

    @Test
    fun `a new applicant is asked to confirm their email once the application is in`() {
        givenAContributionPeriod()
        MembershipSignUpHelper.open(page, frontendUrl)

        val credentials = createAccountThroughUi(page)
        val user = pollForUser(credentials.username)

        // The account exists but is unconfirmed, and the link is already on its way.
        assertThat(user.email).isEqualTo(credentials.email)
        assertThat(user.enabled).isFalse()
        assertThat(TestHelper.findRoles(credentials.username)).contains("GUEST")
        assertThat(TestHelper.hasMemberProfile(user.id)).isTrue()
        TestHelper.assertEmailSent(credentials.email, CONFIRMATION_SUBJECT)

        saveAddressThroughUi(page)
        submitApplicationThroughUi(page)

        assertPw(MembershipSignUpHelper.confirmEmailStep(page)).isVisible()
        assertPw(MembershipSignUpHelper.resendButton(page)).isVisible()

        pollFor("the acceptance is recorded once the application is submitted") {
            TestHelper.conditionsAcceptedAt(user.id) != null
        }
        assertThat(TestHelper.membershipCountForUser(user.id))
            .describedAs("an unconfirmed address must not yield a membership")
            .isZero()
        assertThat(TestHelper.findRoles(credentials.username)).doesNotContain("MEMBER")
    }

    @Test
    fun `confirming the address after applying starts the membership`() {
        givenAContributionPeriod()
        MembershipSignUpHelper.open(page, frontendUrl)

        val credentials = createAccountThroughUi(page)
        val user = pollForUser(credentials.username)
        saveAddressThroughUi(page)
        submitApplicationThroughUi(page)
        assertPw(MembershipSignUpHelper.confirmEmailStep(page)).isVisible()

        confirmAddressThroughUi(page, credentials.username)

        pollFor("membership starts when the second fact arrives", timeoutMs = 10_000) {
            TestHelper.hasActiveMembership(credentials.username)
        }
        assertThat(refreshedUser(credentials.username).enabled).isTrue()
        assertThat(TestHelper.findRoles(credentials.username)).contains("MEMBER")
        assertThat(TestHelper.membershipCountForUser(user.id))
            .describedAs("the rendezvous may only produce one membership")
            .isEqualTo(1)
    }

    @Test
    fun `a mistyped address can be corrected from the confirmation step`() {
        givenAContributionPeriod()
        MembershipSignUpHelper.open(page, frontendUrl)

        val credentials = createAccountThroughUi(page)
        pollForUser(credentials.username)
        saveAddressThroughUi(page)
        submitApplicationThroughUi(page)
        assertPw(MembershipSignUpHelper.confirmEmailStep(page)).isVisible()

        val corrected = "corrected-${credentials.username}@example.com"
        MembershipSignUpHelper.correctEmailButton(page).click()
        val field = MembershipSignUpHelper.correctedEmailField(page)
        assertPw(field).isVisible()
        field.fill(corrected)
        val response = page.waitForResponse(
            Predicate { it.request().method() == "PATCH" && it.url().endsWith("/signup/email") },
        ) {
            MembershipSignUpHelper.correctedEmailSubmitButton(page).click()
        }
        assertThat(response.status()).isEqualTo(204)

        TestHelper.assertEmailSent(corrected, CONFIRMATION_SUBJECT)
        assertThat(refreshedUser(credentials.username).email).isEqualTo(corrected)
    }

    @Test
    fun `details can still be corrected after the application is in`() {
        givenAContributionPeriod()
        MembershipSignUpHelper.open(page, frontendUrl)

        val credentials = createAccountThroughUi(page)
        val user = pollForUser(credentials.username)
        saveAddressThroughUi(page)
        submitApplicationThroughUi(page)
        assertPw(MembershipSignUpHelper.confirmEmailStep(page)).isVisible()

        // Previous, step by step, back to the details.
        MembershipSignUpHelper.confirmBackButton(page).click()
        assertPw(MembershipSignUpHelper.conditionsAccepted(page)).isVisible()
        MembershipSignUpHelper.conditionsBackButton(page).click()
        assertPw(MembershipSignUpHelper.addressNextButton(page)).isVisible()
        MembershipSignUpHelper.addressBackButton(page).click()
        val field = UserFormHelper.firstNameInput(page)
        assertPw(field).isVisible()
        field.fill("Corrected")
        val response = page.waitForResponse(
            Predicate { it.request().method() == "PATCH" && it.url().endsWith("/signup/details") },
        ) {
            MembershipSignUpHelper.detailsNextButton(page).click()
        }
        assertThat(response.status()).isEqualTo(204)

        pollFor("the corrected name reaches the account") {
            TestHelper.firstNameOf(credentials.username) == "Corrected"
        }
        // Editing is not re-applying: the acceptance and the address stay as they were.
        assertThat(TestHelper.conditionsAcceptedAt(user.id)).isNotNull()
        assertThat(TestHelper.findAddress(credentials.username)).isNotNull()
    }

    @Test
    fun `the agreement cannot be withdrawn once the application is in`() {
        givenAContributionPeriod()
        MembershipSignUpHelper.open(page, frontendUrl)

        createAccountThroughUi(page)
        saveAddressThroughUi(page)
        submitApplicationThroughUi(page)

        MembershipSignUpHelper.confirmBackButton(page).click()

        assertPw(MembershipSignUpHelper.conditionsAccepted(page)).isVisible()
        assertPw(MembershipSignUpHelper.conditionsSubmitButton(page)).isHidden()
        MembershipSignUpHelper.conditionsContinueButton(page).click()
        assertPw(MembershipSignUpHelper.confirmEmailStep(page)).isVisible()
    }

    @Test
    fun `the confirmation email can be sent again`() {
        givenAContributionPeriod()
        MembershipSignUpHelper.open(page, frontendUrl)

        val credentials = createAccountThroughUi(page)
        saveAddressThroughUi(page)
        submitApplicationThroughUi(page)
        assertPw(MembershipSignUpHelper.confirmEmailStep(page)).isVisible()

        val response = page.waitForResponse(
            Predicate { it.request().method() == "POST" && it.url().contains("/recovery/user/activate/resend/") },
        ) {
            MembershipSignUpHelper.resendButton(page).click()
        }
        assertThat(response.status()).isEqualTo(204)

        pollFor("a second confirmation email is delivered", timeoutMs = 10_000) {
            TestHelper.findEmails(recipient = credentials.email, subject = CONFIRMATION_SUBJECT).size >= 2
        }
    }

    @Test
    fun `a signed-in applicant becomes a member without a confirmation step`() {
        givenAContributionPeriod()

        val seeded = TestHelper.registerActivateAndPromote("GUEST")
        TestHelper.attachMemberProfile(seeded)
        TestHelper.attachAddress(
            user = seeded,
            city = "Enschede",
            street = "System Test Street",
            houseNumber = "42A",
            zipCode = "7521AB",
        )
        val seededId = TestHelper.findUser(seeded.username)!!.id

        assertThat(AuthHelper.submitLogin(page, frontendUrl, seeded.username, seeded.password)).isEqualTo(200)
        MembershipSignUpHelper.open(page, frontendUrl)

        // Already confirmed, so the details step leads straight on and the flow
        // ends on the membership itself rather than on a confirmation prompt.
        page.waitForResponse(
            Predicate { it.request().method() == "PUT" && it.url().contains("/users/") },
        ) {
            MembershipSignUpHelper.detailsNextButton(page).click()
        }
        saveAddressThroughUi(page, signup = false)
        val outcome = page.waitForResponse(
            Predicate { it.request().method() == "POST" && it.url().endsWith("/memberships") },
        ) {
            acceptConditions(page)
            MembershipSignUpHelper.conditionsSubmitButton(page).click()
        }
        assertThat(outcome.status()).isEqualTo(200)

        assertPw(MembershipSignUpHelper.completePanel(page)).isVisible()
        assertPw(MembershipSignUpHelper.confirmEmailStep(page)).isHidden()
        pollFor("membership exists for a confirmed applicant", timeoutMs = 10_000) {
            TestHelper.hasActiveMembership(seeded.username)
        }
        assertThat(TestHelper.findRoles(seeded.username)).contains("MEMBER")
        assertThat(TestHelper.conditionsAcceptedAt(seededId)).isNotNull()
    }

    private data class Credentials(val username: String, val email: String, val password: String)

    private fun givenAContributionPeriod() {
        TestHelper.createContributionPeriod(
            LocalDate.now().minusDays(15),
            LocalDate.now().plusDays(345),
        )
    }

    private fun createAccountThroughUi(page: Page): Credentials {
        val suffix = System.currentTimeMillis().toString().takeLast(8)
        val username = "sysuser$suffix"
        val email = "sysuser$suffix@example.com"
        val password = "Passw0rd!$suffix"

        UserFormHelper.fill(
            page = page,
            fields = UserFormHelper.Fields(
                initials = "SU",
                firstName = "System",
                surname = "User$suffix",
                username = username,
                discord = "sysuser$suffix",
                email = email,
                phoneNumber = "+3161${suffix.takeLast(7)}",
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

        val response = page.waitForResponse(
            Predicate { it.request().method() == "POST" && it.url().endsWith("/signup") },
        ) {
            MembershipSignUpHelper.detailsNextButton(page).click()
        }
        assertThat(response.status()).isEqualTo(201)

        return Credentials(username, email, password)
    }

    private fun saveAddressThroughUi(page: Page, signup: Boolean = true) {
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
        val path = if (signup) "/signup/address" else "/addresses"
        val response = page.waitForResponse(
            Predicate { it.request().method() in setOf("POST", "PUT") && it.url().contains(path) },
        ) {
            MembershipSignUpHelper.addressNextButton(page).click()
        }
        assertThat(response.status()).isBetween(200, 299)
    }

    private fun submitApplicationThroughUi(page: Page) {
        acceptConditions(page)
        val response = page.waitForResponse(
            Predicate { it.request().method() == "POST" && it.url().endsWith("/signup/apply") },
        ) {
            MembershipSignUpHelper.conditionsSubmitButton(page).click()
        }
        assertThat(response.status()).isEqualTo(200)
    }

    private fun acceptConditions(page: Page) {
        val checkbox = membershipConsentCheckbox(page)
        assertPw(checkbox).isVisible()
        if (!checkbox.isChecked) checkbox.check()
        pollFor("membership consent checkbox checked", timeoutMs = 5_000) { checkbox.isChecked }
    }

    private fun confirmAddressThroughUi(page: Page, username: String) {
        val token = URLEncoder.encode(
            TestHelper.mintRecoveryToken(username, "USER_ACTIVATION"),
            StandardCharsets.UTF_8,
        )
        val response = page.waitForResponse("**/recovery/user/activate") {
            page.navigate("$frontendUrl/account/activate/user#token=$token")
        }
        assertThat(response.status()).isEqualTo(200)
    }

    private fun membershipConsentCheckbox(page: Page) = page.getByRole(
        AriaRole.CHECKBOX,
        Page.GetByRoleOptions()
            .setName(MEMBERSHIP_CONSENT_LABEL_PREFIX)
            .setExact(false),
    )

    private fun refreshedUser(username: String) = requireNotNull(TestHelper.findUser(username))

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
        const val CONFIRMATION_SUBJECT = "Activate your Account"
    }
}
