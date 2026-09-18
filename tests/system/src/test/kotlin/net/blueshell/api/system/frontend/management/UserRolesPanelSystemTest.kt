package net.blueshell.api.system.frontend.management

import com.microsoft.playwright.options.AriaRole
import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.api.system.frontend.helper.TestIdLocatorHelper
import net.blueshell.api.system.frontend.helper.UserManagerHelper
import net.blueshell.systemtests.PlaywrightTestBase
import net.blueshell.systemtests.TestHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.function.Predicate

/**
 * An admin granting a role, through the browser: the state the panel shows afterwards, and the
 * history entry the change leaves behind.
 */
@Tag("system")
class UserRolesPanelSystemTest : PlaywrightTestBase() {
    @Test
    fun `an admin grants board and sees the new state and a history entry`() {
        val admin = TestHelper.registerActivateAndPromote("ADMIN")
        val target = TestHelper.registerActivateAndPromote("GUEST")
        val targetId = TestHelper.findUser(target.username)!!.id

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, admin.username, admin.password)
        assertThat(loginStatus).isEqualTo(200)

        UserManagerHelper.open(page, frontendUrl)
        UserManagerHelper.search(page, target.username)
        page.locator("[data-testid='member-manager-row-$targetId']").first().waitFor()

        UserManagerHelper.clickEditRoles(page, targetId)
        TestIdLocatorHelper.byTestId(page, "user-roles-dialog").waitFor()

        // The request rather than the response: a save that lands re-reads the history, and
        // waiting on the response races the render that follows it.
        page.waitForRequest(
            Predicate { request ->
                request.method() == "PUT" && request.url().contains("/users/$targetId/roles")
            },
        ) {
            TestIdLocatorHelper
                .byTestId(page, "user-roles-checkbox-board")
                .getByRole(AriaRole.CHECKBOX)
                .first()
                .check()
            TestIdLocatorHelper
                .byTestId(page, "user-roles-note")
                .locator("input")
                .first()
                .fill("Took office today")
            TestIdLocatorHelper.byTestId(page, "user-roles-save-btn").click()
        }

        val history = TestIdLocatorHelper.byTestId(page, "user-roles-history")
        history.waitFor()
        assertThat(history.textContent()).contains("board").contains("Took office today")

        // Guest is the account's own role, so the panel says so rather than offering a box.
        TestIdLocatorHelper.byTestId(page, "user-roles-derived-guest").waitFor()
    }
}
