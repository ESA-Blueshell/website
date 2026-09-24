package net.blueshell.api.system.frontend.validation

import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.api.system.frontend.helper.UserFormHelper
import net.blueshell.systemtests.PlaywrightTestBase
import net.blueshell.systemtests.TestHelper
import net.blueshell.systemtests.awaitResponseFrom
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat as assertPw

@Tag("system")
class UserValidationSystemTest : PlaywrightTestBase() {
    @Test
    fun `create account rejects duplicate username`() {
        val suffix = TestHelper.uniqueSuffix()
        val existingGuest =
            TestHelper.registerActivateAndPromote(
                role = "GUEST",
                username = "guest$suffix",
            )

        val candidateSuffix = TestHelper.uniqueSuffix()
        page.navigate("$frontendUrl/account/create")
        UserFormHelper.fill(
            page = page,
            fields =
                UserFormHelper.Fields(
                    initials = "VA",
                    firstName = "Validation",
                    surname = "Case",
                    username = existingGuest.username,
                    discord = "unique$candidateSuffix",
                    email = "unique$candidateSuffix@example.com",
                    phoneNumber = "+3161${candidateSuffix.takeLast(7)}",
                    password = "Passw0rd!$candidateSuffix",
                    repeatedPassword = "Passw0rd!$candidateSuffix",
                ),
        )
        UserFormHelper.acceptPrivacyConsentIfVisible(page)

        val createResponse =
            page.awaitResponseFrom(
                control = UserFormHelper.submitButton(page),
                expected = "POST /signup",
            ) { it.request().method() == "POST" && it.url().endsWith("/signup") }

        assertThat(createResponse.status()).isEqualTo(400)
        page.getByText("Username is taken.").first().waitFor()
        assertThat(page.locator("[data-testid='create-account-success-state']").count()).isEqualTo(0)
    }

    @Test
    fun `create account rejects duplicate phone number`() {
        val suffix = TestHelper.uniqueSuffix()
        val existingGuest =
            TestHelper.registerActivateAndPromote(
                role = "GUEST",
                username = "phoneguest$suffix",
                phoneNumber = "+3161${suffix.takeLast(7)}",
            )

        val candidateSuffix = TestHelper.uniqueSuffix()
        page.navigate("$frontendUrl/account/create")
        UserFormHelper.fill(
            page = page,
            fields =
                UserFormHelper.Fields(
                    initials = "VA",
                    firstName = "Validation",
                    surname = "Case",
                    username = "uniquename$candidateSuffix",
                    discord = "uniquephone$candidateSuffix",
                    email = "uniquephone$candidateSuffix@example.com",
                    phoneNumber = existingGuest.phoneNumber,
                    password = "Passw0rd!$candidateSuffix",
                    repeatedPassword = "Passw0rd!$candidateSuffix",
                ),
        )
        UserFormHelper.acceptPrivacyConsentIfVisible(page)

        val createResponse =
            page.awaitResponseFrom(
                control = UserFormHelper.submitButton(page),
                expected = "POST /signup",
            ) { it.request().method() == "POST" && it.url().endsWith("/signup") }

        assertThat(createResponse.status()).isEqualTo(400)
        page.getByText("Phone number is taken.").first().waitFor()
        assertThat(page.locator("[data-testid='create-account-success-state']").count()).isEqualTo(0)
    }

    /**
     * Server names are not unique, so two accounts may carry the same one; what is unique is the
     * linked member, which needs the bot this stack runs without.
     */
    @Test
    fun `account update accepts a Discord name another account carries`() {
        // Two numbers nothing else holds: phone_number is unique, and a pair built from one
        // clock reading is the collision this test is not about.
        val primaryUser =
            TestHelper.registerActivateAndPromote(
                role = "GUEST",
                phoneNumber = TestHelper.uniquePhoneNumber(),
            )
        val secondaryUser =
            TestHelper.registerActivateAndPromote(
                role = "GUEST",
                phoneNumber = TestHelper.uniquePhoneNumber(),
            )
        val secondaryId = TestHelper.findUser(secondaryUser.username)!!.id

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, secondaryUser.username, secondaryUser.password)
        assertThat(loginStatus).isEqualTo(200)

        page.navigate("$frontendUrl/account")
        page.waitForURL("**/account")
        page.locator("[data-testid='account-user-form']").first().waitFor()

        val discordField = UserFormHelper.discordInput(page)
        assertPw(discordField).hasValue(secondaryUser.discord)

        val updateResponse =
            page.awaitResponseFrom(
                control = UserFormHelper.submitButton(page),
                expected = "PUT /users/$secondaryId",
                act = {
                    discordField.fill(primaryUser.discord)
                    UserFormHelper.submitButton(page).click()
                },
            ) { it.request().method() == "PUT" && it.url().contains("/users/$secondaryId") }

        assertThat(updateResponse.status()).isEqualTo(200)
        assertPw(page.locator("[data-testid='user-form-discord-field']").getByText("Discord is taken.")).not().isVisible()
    }
}
