package net.blueshell.api.system.frontend.management

import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.api.system.frontend.helper.UserManagerHelper
import net.blueshell.systemtests.PlaywrightTestBase
import net.blueshell.systemtests.TestHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.function.Predicate

@Tag("system")
class UserManagerPageSystemTest : PlaywrightTestBase() {

    // Membership lifecycle actions (start/end/period visibility) moved off the
    // manager table into the edit-membership modal (#386); those flows are
    // covered there. The manager table now shows every user in one grid, so the
    // remaining page-level behaviour to guard is user deletion.

    @Test
    fun `deleted user stays visible in member manager as anonymized row`() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        val target = TestHelper.registerActivateAndPromote("GUEST")
        val targetId = TestHelper.findUser(target.username)!!.id

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)

        UserManagerHelper.open(page, frontendUrl)
        UserManagerHelper.search(page, target.username)
        page.locator("[data-testid='member-manager-row-$targetId']").first().waitFor()

        val deleteResponse = page.waitForResponse(
            Predicate { response ->
                response.request().method() == "DELETE" &&
                    response.url().contains("/users/$targetId")
            },
        ) {
            UserManagerHelper.clickDeleteUser(page, targetId)
            UserManagerHelper.confirmDelete(page)
        }
        assertThat(deleteResponse.status()).isEqualTo(204)

        UserManagerHelper.open(page, frontendUrl)

        // Deletion anonymizes the user (username scrubbed to a placeholder), so the
        // still-present row is located by id among all rows, not by the old username.
        page.locator("[data-testid='member-manager-row-$targetId']").first().waitFor()
    }
}
