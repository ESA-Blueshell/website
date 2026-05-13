package net.blueshell.api.system.frontend.validation

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat as assertPw
import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.api.system.frontend.helper.UserFormHelper
import net.blueshell.systemtests.PlaywrightTestBase
import net.blueshell.systemtests.TestHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("system")
class UserValidationSystemTest : PlaywrightTestBase() {

    @Test
    fun `create account rejects duplicate username`() {
        val suffix = System.currentTimeMillis().toString().takeLast(8)
        val existingGuest = TestHelper.registerActivateAndPromote(
            role = "GUEST",
            username = "guest$suffix",
        )

        val candidateSuffix = System.currentTimeMillis().toString().takeLast(8)
        page.navigate("$frontendUrl/account/create")
        UserFormHelper.fill(
            page = page,
            fields = UserFormHelper.Fields(
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

        val createResponse = page.waitForResponse({ response ->
            response.request().method() == "POST" && response.url().contains("/users")
        }) {
            UserFormHelper.submitButton(page).click()
        }

        assertThat(createResponse.status()).isEqualTo(400)
        page.getByText("Username is taken.").first().waitFor()
        assertThat(page.locator("[data-testid='create-account-success-state']").count()).isEqualTo(0)
    }

    @Test
    fun `create account rejects duplicate phone number`() {
        val suffix = System.currentTimeMillis().toString().takeLast(8)
        val existingGuest = TestHelper.registerActivateAndPromote(
            role = "GUEST",
            username = "phoneguest$suffix",
            phoneNumber = "+3161${suffix.takeLast(7)}",
        )

        val candidateSuffix = System.currentTimeMillis().toString().takeLast(8)
        page.navigate("$frontendUrl/account/create")
        UserFormHelper.fill(
            page = page,
            fields = UserFormHelper.Fields(
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

        val createResponse = page.waitForResponse({ response ->
            response.request().method() == "POST" && response.url().contains("/users")
        }) {
            UserFormHelper.submitButton(page).click()
        }

        assertThat(createResponse.status()).isEqualTo(400)
        page.getByText("Phone number is taken.").first().waitFor()
        assertThat(page.locator("[data-testid='create-account-success-state']").count()).isEqualTo(0)
    }

    @Test
    fun `account update rejects duplicate discord`() {
        val seed = System.currentTimeMillis() % 10_000_000
        val primaryUser = TestHelper.registerActivateAndPromote(
            role = "GUEST",
            phoneNumber = "+3161${seed.toString().padStart(7, '0')}",
        )
        val secondaryUser = TestHelper.registerActivateAndPromote(
            role = "GUEST",
            phoneNumber = "+3161${((seed + 1) % 10_000_000).toString().padStart(7, '0')}",
        )
        val secondaryId = TestHelper.findUser(secondaryUser.username)!!.id

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, secondaryUser.username, secondaryUser.password)
        assertThat(loginStatus).isEqualTo(200)

        page.navigate("$frontendUrl/account")
        page.waitForURL("**/account")
        page.locator("[data-testid='account-user-form']").first().waitFor()

        val discordField = UserFormHelper.discordInput(page)
        assertPw(discordField).hasValue(secondaryUser.discord)

        val updateResponse = page.waitForResponse({ response ->
            response.request().method() == "PUT" && response.url().contains("/users/$secondaryId")
        }) {
            discordField.fill(primaryUser.discord)
            UserFormHelper.submitButton(page).click()
        }

        assertThat(updateResponse.status()).isEqualTo(400)
        assertPw(page.locator("[data-testid='user-form-discord-field']").getByText("Discord is taken.")).isVisible()
    }
}
