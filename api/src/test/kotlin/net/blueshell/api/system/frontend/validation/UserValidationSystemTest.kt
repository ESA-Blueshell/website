package net.blueshell.api.system.frontend.validation

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat as assertPw
import net.blueshell.api.factory.user.persistence.UserFactory
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.system.frontend.FrontendSystemTestBase
import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.api.system.frontend.helper.UserFormHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@Tag("system")
class UserValidationSystemTest : FrontendSystemTestBase() {

    @Autowired
    private lateinit var userFactory: UserFactory

    @Test
    fun `create account rejects duplicate username`() {
        val suffix = System.currentTimeMillis().toString().takeLast(8)
        val existingGuest = userFactory.buildUserWithRole(Role.GUEST, enabled = true).apply {
            username = "guest$suffix"
            email = "guest$suffix@example.com"
            discord = "guest$suffix"
            phoneNumber = "+3161${suffix.takeLast(7)}"
        }.let(userRepository::saveAndFlush)

        withPage { page ->
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
                    repeatedPassword = "Passw0rd!$candidateSuffix"
                )
            )
            UserFormHelper.acceptPrivacyConsentIfVisible(page)

            val createResponse = page.waitForResponse({ response ->
                response.request().method() == "POST" && response.url().contains("/users")
            }) {
                UserFormHelper.submitButton(page).click()
            }

            assertThat(createResponse.status()).isEqualTo(400)
            assertThat(page.locator("[data-testid='user-form-username-field']").count()).isGreaterThan(0)
            waitFor(
                timeoutMs = 10_000,
                intervalMs = 200,
                onTimeoutMessage = { "Expected username duplicate validation message" }
            ) { page.getByText("Username is taken.").count() > 0 }
            assertThat(page.locator("[data-testid='create-account-success-state']").count()).isEqualTo(0)
        }
    }

    @Test
    fun `create account rejects duplicate phone number`() {
        val suffix = System.currentTimeMillis().toString().takeLast(8)
        val existingGuest = userFactory.buildUserWithRole(Role.GUEST, enabled = true).apply {
            username = "phoneguest$suffix"
            email = "phoneguest$suffix@example.com"
            discord = "phoneguest$suffix"
            phoneNumber = "+3161${suffix.takeLast(7)}"
        }.let(userRepository::saveAndFlush)

        withPage { page ->
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
                    repeatedPassword = "Passw0rd!$candidateSuffix"
                )
            )
            UserFormHelper.acceptPrivacyConsentIfVisible(page)

            val createResponse = page.waitForResponse({ response ->
                response.request().method() == "POST" && response.url().contains("/users")
            }) {
                UserFormHelper.submitButton(page).click()
            }

            assertThat(createResponse.status()).isEqualTo(400)
            assertThat(page.locator("[data-testid='user-form-phone-number-field']").count()).isGreaterThan(0)
            waitFor(
                timeoutMs = 10_000,
                intervalMs = 200,
                onTimeoutMessage = { "Expected phone duplicate validation message" }
            ) { page.getByText("Phone number is taken.").count() > 0 }
            assertThat(page.locator("[data-testid='create-account-success-state']").count()).isEqualTo(0)
        }
    }

    @Test
    fun `account update rejects duplicate discord`() {
        val phoneSeed = System.currentTimeMillis() % 10_000_000
        val primaryUser = userFactory.createUserWithRole(Role.GUEST, enabled = true).apply {
            phoneNumber = "+3161${phoneSeed.toString().padStart(7, '0')}"
        }.let(userRepository::saveAndFlush)
        val secondaryUser = userFactory.createUserWithRole(Role.GUEST, enabled = true).apply {
            phoneNumber = "+3161${((phoneSeed + 1) % 10_000_000).toString().padStart(7, '0')}"
        }.let(userRepository::saveAndFlush)

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, secondaryUser.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            page.navigate("$frontendUrl/account")
            page.waitForURL("**/account")

            val discordField = UserFormHelper.discordInput(page)
            assertPw(discordField).hasValue(secondaryUser.discord)

            val updateResponse = page.waitForResponse({ response ->
                response.request().method() == "PUT" && response.url().contains("/users/${secondaryUser.id}")
            }) {
                discordField.fill(primaryUser.discord)
                UserFormHelper.submitButton(page).click()
            }

            assertThat(updateResponse.status()).isEqualTo(400)
            assertPw(page.locator("[data-testid='user-form-discord-field']").getByText("Discord is taken.")).isVisible()
        }
    }

    private companion object {
        const val DEFAULT_PASSWORD = "Password123!"
    }
}
