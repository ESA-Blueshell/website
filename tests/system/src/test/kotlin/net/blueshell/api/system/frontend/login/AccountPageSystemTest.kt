package net.blueshell.api.system.frontend.login

import com.microsoft.playwright.Page
import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.api.system.frontend.helper.LoginDomainHelper
import net.blueshell.systemtests.PlaywrightTestBase
import net.blueshell.systemtests.TestHelper
import net.blueshell.systemtests.pollForValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("system")
class AccountPageSystemTest : PlaywrightTestBase() {

    @Test
    fun `member can update editable account fields`() {
        val member = TestHelper.registerActivateAndPromote("MEMBER")
        TestHelper.attachMemberProfile(member)
        TestHelper.attachMembership(member.username)

        val suffix = System.currentTimeMillis().toString().takeLast(8)
        val updatedDiscord = "account$suffix"
        val updatedPhone = "+3161${suffix.takeLast(7)}"

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, member.username, member.password)
        assertThat(loginStatus).isEqualTo(200)

        page.navigate("$frontendUrl/account")
        page.waitForURL("**/account")
        // The form wrapper mounts after the page's `/users/{id}` fetch
        // resolves; without waiting for it explicitly, `fill` can race
        // the still-loading VeeValidate setup that briefly leaves the
        // inputs non-editable.
        page.locator("[data-testid='account-user-form']").first().waitFor()

        page.getByLabel("Discord*", Page.GetByLabelOptions().setExact(true)).fill(updatedDiscord)
        page.getByLabel("Phone Number*", Page.GetByLabelOptions().setExact(true)).fill(updatedPhone)
        LoginDomainHelper.clickAccountSubmit(page)

        waitForUserField(member.username) { it.discord == updatedDiscord && it.phoneNumber == updatedPhone }

        val refreshed = TestHelper.findUser(member.username)!!
        assertThat(refreshed.discord).isEqualTo(updatedDiscord)
        assertThat(refreshed.phoneNumber).isEqualTo(updatedPhone)
    }

    private fun waitForUserField(
        username: String,
        predicate: (TestHelper.RegisteredUserRow) -> Boolean,
    ) {
        try {
            pollForValue("user $username to satisfy the predicate") { TestHelper.findUser(username)?.takeIf(predicate) }
        } catch (e: AssertionError) {
            throw AssertionError("${e.message}; last row=${TestHelper.findUser(username)}", e)
        }
    }
}
