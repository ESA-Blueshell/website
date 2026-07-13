package net.blueshell.api.system.frontend.management

import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.api.system.frontend.helper.MemberManagerHelper
import net.blueshell.api.system.frontend.helper.RecoveryManagerHelper
import net.blueshell.systemtests.PlaywrightTestBase
import net.blueshell.systemtests.TestHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.function.Predicate

@Tag("system")
class RecoveryManagerPageSystemTest : PlaywrightTestBase() {

    @Test
    fun `recovery manager resends activation for inactive user`() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        val inactiveUser = TestHelper.register()
        val inactiveId = TestHelper.findUser(inactiveUser.username)!!.id

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)

        RecoveryManagerHelper.open(page, frontendUrl)
        RecoveryManagerHelper.openSection(page, "inactive")
        RecoveryManagerHelper.searchUser(page, "inactive", inactiveUser.username)

        pollFor("inactive user ${inactiveUser.username} visible") {
            RecoveryManagerHelper.rowCount(page, "inactive", inactiveId) > 0
        }

        val response = page.waitForResponse("**/recovery/user/activate/resend/**") {
            RecoveryManagerHelper.clickAction(page, "activation", inactiveId)
        }
        assertThat(response.status()).isEqualTo(204)

        TestHelper.assertEmailSent(inactiveUser.email, "Activate your Account")
    }

    @Test
    fun `recovery manager sends password reset for active user`() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        val activeUser = TestHelper.registerActivateAndPromote("GUEST")
        val activeId = TestHelper.findUser(activeUser.username)!!.id

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)

        RecoveryManagerHelper.open(page, frontendUrl)
        RecoveryManagerHelper.openSection(page, "active")
        RecoveryManagerHelper.searchUser(page, "active", activeUser.username)

        pollFor("active user ${activeUser.username} visible") {
            RecoveryManagerHelper.rowCount(page, "active", activeId) > 0
        }

        val response = page.waitForResponse("**/recovery/password/reset/**") {
            RecoveryManagerHelper.clickAction(page, "password", activeId)
        }
        assertThat(response.status()).isEqualTo(204)

        TestHelper.assertEmailSent(activeUser.email, "Reset Your Blueshell Account Password")
    }

    @Test
    fun `board deletes user from member manager and restores in recovery manager`() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        val target = TestHelper.registerActivateAndPromote("GUEST")
        val targetId = TestHelper.findUser(target.username)!!.id
        val originalUsername = target.username
        val originalEmail = target.email

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)

        MemberManagerHelper.open(page, frontendUrl)
        MemberManagerHelper.search(page, originalUsername)

        pollFor("non-member user $originalUsername visible before deletion") {
            page.locator("[data-testid='member-manager-row-$targetId']").count() > 0
        }

        val deleteResponse = page.waitForResponse(
            Predicate { response ->
                response.request().method() == "DELETE" && response.url().contains("/users/$targetId")
            },
        ) {
            MemberManagerHelper.clickDeleteUser(page, targetId)
            MemberManagerHelper.confirmDelete(page)
        }
        assertThat(deleteResponse.status()).isEqualTo(204)

        pollFor("user row $targetId removed from non-members after deletion") {
            page.locator("[data-testid='member-manager-row-$targetId']").count() == 0
        }

        RecoveryManagerHelper.open(page, frontendUrl)
        RecoveryManagerHelper.openSection(page, "deleted")
        RecoveryManagerHelper.searchUser(page, "deleted", originalUsername)

        pollFor("deleted user $originalUsername in deleted pane") {
            RecoveryManagerHelper.rowCount(page, "deleted", targetId) > 0
        }

        val restoreResponse = page.waitForResponse(
            Predicate { response ->
                response.request().method() == "PUT" && response.url().contains("/users/$targetId/restore")
            },
        ) {
            RecoveryManagerHelper.clickAction(page, "restore", targetId)
        }
        assertThat(restoreResponse.status()).isEqualTo(204)

        pollFor("deleted recovery row for user $targetId removed after restore") {
            RecoveryManagerHelper.rowCount(page, "deleted", targetId) == 0
        }

        RecoveryManagerHelper.openSection(page, "active")
        RecoveryManagerHelper.searchUser(page, "active", originalUsername)

        pollFor("restored user $originalUsername visible in active pane") {
            RecoveryManagerHelper.rowCount(page, "active", targetId) > 0
        }

        pollFor("deleted snapshot removed and user restored in persistence layer") {
            val restored = TestHelper.findUserById(targetId)
            restored != null &&
                restored.username == originalUsername &&
                restored.email == originalEmail &&
                !TestHelper.hasDeletedUserSnapshot(targetId)
        }
    }

    @Test
    fun `deleted user remains visible in inactive pane while also present in deleted pane`() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        val target = TestHelper.registerActivateAndPromote("GUEST")
        val targetId = TestHelper.findUser(target.username)!!.id

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)

        MemberManagerHelper.open(page, frontendUrl)
        MemberManagerHelper.search(page, target.username)

        pollFor("target user ${target.username} visible before deletion") {
            page.locator("[data-testid='member-manager-row-$targetId']").count() > 0
        }

        val deleteResponse = page.waitForResponse(
            Predicate { response ->
                response.request().method() == "DELETE" && response.url().contains("/users/$targetId")
            },
        ) {
            MemberManagerHelper.clickDeleteUser(page, targetId)
            MemberManagerHelper.confirmDelete(page)
        }
        assertThat(deleteResponse.status()).isEqualTo(204)

        RecoveryManagerHelper.open(page, frontendUrl)

        RecoveryManagerHelper.openSection(page, "deleted")
        pollFor("deleted user $targetId visible in deleted pane") {
            RecoveryManagerHelper.rowCount(page, "deleted", targetId) > 0
        }

        RecoveryManagerHelper.openSection(page, "inactive")
        pollFor("deleted user $targetId visible in inactive pane as anonymized row") {
            RecoveryManagerHelper.rowCount(page, "inactive", targetId) > 0
        }
    }

    private fun pollFor(description: String, timeoutMs: Long = 10_000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            Thread.sleep(200)
        }
        throw AssertionError("Expected $description within ${timeoutMs}ms")
    }
}
