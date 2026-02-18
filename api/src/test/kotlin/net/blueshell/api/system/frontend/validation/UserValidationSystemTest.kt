package net.blueshell.api.system.frontend.validation

import com.microsoft.playwright.Page
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat as assertPw
import com.microsoft.playwright.options.AriaRole
import net.blueshell.api.factory.user.persistence.UserFactory
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.system.frontend.FrontendSystemTestBase
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
            fillCreateAccountForm(
                page = page,
                username = existingGuest.username,
                email = "unique$candidateSuffix@example.com",
                discord = "unique$candidateSuffix",
                phoneNumber = "+3161${candidateSuffix.takeLast(7)}",
                password = "Passw0rd!$candidateSuffix"
            )

            page.getByRole(
                AriaRole.BUTTON,
                Page.GetByRoleOptions().setName("Create Account").setExact(false)
            ).click()

            assertPw(page.getByText("Username is taken.")).isVisible()
            assertThat(page.getByText("Your account has successfully been created!").count()).isEqualTo(0)
        }
    }

    @Test
    fun `create account blocks invalid input client side`() {
        withPage { page ->
            val suffix = System.currentTimeMillis().toString().takeLast(8)
            fillCreateAccountForm(
                page = page,
                username = "invalid-user-$suffix",
                email = "not-an-email",
                discord = "frontend$suffix",
                phoneNumber = "+3164444$suffix",
                password = "Password123"
            )

            page.getByRole(
                AriaRole.BUTTON,
                Page.GetByRoleOptions().setName("Create Account").setExact(false)
            ).click()

            assertPw(page.getByText("Use only letters and numbers")).isVisible()
            assertPw(page.getByText("Enter a valid e-mail address")).isVisible()
            assertPw(page.getByText("Include a special char (@$!%*?&)")).isVisible()
            assertThat(page.getByText("Your account has successfully been created!").count()).isEqualTo(0)
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
            fillCreateAccountForm(
                page = page,
                username = "uniquename$candidateSuffix",
                email = "uniquephone$candidateSuffix@example.com",
                discord = "uniquephone$candidateSuffix",
                phoneNumber = existingGuest.phoneNumber,
                password = "Passw0rd!$candidateSuffix"
            )

            page.getByRole(
                AriaRole.BUTTON,
                Page.GetByRoleOptions().setName("Create Account").setExact(false)
            ).click()

            assertPw(page.getByText("Phone number is taken.")).isVisible()
            assertThat(page.getByText("Your account has successfully been created!").count()).isEqualTo(0)
        }
    }

    @Test
    fun `account update rejects duplicate discord`() {
        val primaryMember = userFactory.createUserWithRole(Role.MEMBER, enabled = true)
        primaryMember.replaceMemberProfile(userFactory.buildMemberProfile(primaryMember))
        userRepository.saveAndFlush(primaryMember)
        userFactory.createMembership(primaryMember)
        val conflictingGuest = userFactory.createUserWithRole(Role.GUEST, enabled = true)
        val discordBefore = primaryMember.discord

        withPage { page ->
            loginThroughUi(page, primaryMember.username, DEFAULT_PASSWORD)

            page.navigate("$frontendUrl/account")
            page.waitForURL("**/account")

            page.getByLabel("Discord", Page.GetByLabelOptions().setExact(false)).fill(conflictingGuest.discord)
            page.getByRole(
                AriaRole.BUTTON,
                Page.GetByRoleOptions().setName("Submit").setExact(false)
            ).click()

            assertPw(page.getByText("Discord is taken.")).isVisible()
        }

        val updated = waitForOptional(
            producer = { userRepository.findByUsername(primaryMember.username) },
            onTimeoutMessage = { "Expected member ${primaryMember.username} to exist after update attempt" }
        )
        assertThat(updated.discord).isEqualTo(discordBefore)
    }

    private fun loginThroughUi(page: Page, username: String, password: String) {
        page.navigate("$frontendUrl/login/")
        page.getByLabel("Username").fill(username)
        page.getByRole(
            AriaRole.TEXTBOX,
            Page.GetByRoleOptions().setName("Password")
        ).fill(password)

        val response = page.waitForResponse("**/auth") {
            page.getByRole(
                AriaRole.BUTTON,
                Page.GetByRoleOptions().setName("Login")
            ).click()
        }
        assertThat(response.status()).isEqualTo(200)
    }

    private fun fillCreateAccountForm(
        page: Page,
        username: String,
        email: String,
        discord: String,
        phoneNumber: String,
        password: String
    ) {
        page.navigate("$frontendUrl/account/create")
        page.getByLabel("Initials*", Page.GetByLabelOptions().setExact(true)).fill("VA")
        page.getByLabel("First Name*", Page.GetByLabelOptions().setExact(true)).fill("Validation")
        page.getByLabel("Surname*", Page.GetByLabelOptions().setExact(true)).fill("Case")
        page.getByLabel("Username*", Page.GetByLabelOptions().setExact(true)).fill(username)
        page.getByLabel("Discord*", Page.GetByLabelOptions().setExact(true)).fill(discord)
        page.getByLabel("E-mail*", Page.GetByLabelOptions().setExact(true)).fill(email)
        page.getByLabel("Phone Number*", Page.GetByLabelOptions().setExact(true)).fill(phoneNumber)
        page.getByLabel("Password*", Page.GetByLabelOptions().setExact(true)).fill(password)
        page.getByLabel("Password (repeated)", Page.GetByLabelOptions().setExact(true)).fill(password)
    }

    private companion object {
        const val DEFAULT_PASSWORD = "Password123!"
    }
}
