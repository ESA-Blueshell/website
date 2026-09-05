package net.blueshell.api.system.frontend.management

import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.api.system.frontend.helper.UserManagerHelper
import net.blueshell.api.system.frontend.helper.RecoveryManagerHelper
import net.blueshell.systemtests.PlaywrightTestBase
import net.blueshell.systemtests.TestHelper
import net.blueshell.systemtests.pollFor
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

        // Reading the email is how it is sent: the row button renders it, the dialog sends it.
        val rendered = page.waitForResponse("**/recovery/users/*/email-preview**") {
            RecoveryManagerHelper.openEmail(page, "USER_ACTIVATION", inactiveId)
        }
        assertThat(rendered.status()).isEqualTo(200)

        RecoveryManagerHelper.confirmSend(page)

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

        val rendered = page.waitForResponse("**/recovery/users/*/email-preview**") {
            RecoveryManagerHelper.openEmail(page, "PASSWORD_RESET", activeId)
        }
        assertThat(rendered.status()).isEqualTo(200)

        val response = page.waitForResponse("**/recovery/password/reset/**") {
            RecoveryManagerHelper.confirmSend(page)
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

        UserManagerHelper.open(page, frontendUrl)
        UserManagerHelper.search(page, originalUsername)

        pollFor("non-member user $originalUsername visible before deletion") {
            page.locator("[data-testid='member-manager-row-$targetId']").count() > 0
        }

        UserManagerHelper.clickDeleteUser(page, targetId)
        UserManagerHelper.confirmDelete(page)

        pollFor("user row $targetId removed from non-members after deletion") {
            page.locator("[data-testid='member-manager-row-$targetId']").count() == 0
        }

        RecoveryManagerHelper.open(page, frontendUrl)
        RecoveryManagerHelper.openSection(page, "deleted")
        RecoveryManagerHelper.searchUser(page, "deleted", originalUsername)

        pollFor("deleted user $originalUsername in deleted pane") {
            RecoveryManagerHelper.rowCount(page, "deleted", targetId) > 0
        }

        RecoveryManagerHelper.clickAction(page, "restore", targetId)

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

        UserManagerHelper.open(page, frontendUrl)
        UserManagerHelper.search(page, target.username)

        pollFor("target user ${target.username} visible before deletion") {
            page.locator("[data-testid='member-manager-row-$targetId']").count() > 0
        }

        val deleteResponse = page.waitForResponse(
            Predicate { response ->
                response.request().method() == "DELETE" && response.url().contains("/users/$targetId")
            },
        ) {
            UserManagerHelper.clickDeleteUser(page, targetId)
            UserManagerHelper.confirmDelete(page)
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

    @Test
    fun `recovery manager previews an activation email without sending it`() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        val inactiveUser = TestHelper.register()
        val inactiveId = TestHelper.findUser(inactiveUser.username)!!.id
        val linksBefore = TestHelper.outstandingRecoveryLinks(inactiveUser.username, "USER_ACTIVATION")
        val emailsBefore = TestHelper.findEmails(recipient = inactiveUser.email).size

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)

        RecoveryManagerHelper.open(page, frontendUrl)
        RecoveryManagerHelper.openSection(page, "inactive")
        RecoveryManagerHelper.searchUser(page, "inactive", inactiveUser.username)

        pollFor("inactive user ${inactiveUser.username} visible") {
            RecoveryManagerHelper.rowCount(page, "inactive", inactiveId) > 0
        }

        // A self-signup takes the ordinary activation, and the row offers that one alone.
        assertThat(RecoveryManagerHelper.offersEmail(page, "MEMBER_ACTIVATION", inactiveId)).isFalse()

        val response = page.waitForResponse("**/recovery/users/*/email-preview**") {
            RecoveryManagerHelper.openEmail(page, "USER_ACTIVATION", inactiveId)
        }
        assertThat(response.status()).isEqualTo(200)

        val subject = page.locator("[data-testid='email-preview-subject']")
        subject.waitFor()
        assertThat(subject.textContent()).isEqualTo("Activate your Account")
        // The reader is told the link does not work, because it carries no token.
        assertThat(page.locator("[data-testid='email-preview-placeholder-notice']").isVisible).isTrue()
        assertThat(page.locator("[data-testid='email-preview-frame']").getAttribute("sandbox")).isEmpty()

        // Reading the email left the account exactly as it was.
        assertThat(TestHelper.outstandingRecoveryLinks(inactiveUser.username, "USER_ACTIVATION"))
            .describedAs("outstanding activation links after a preview")
            .isEqualTo(linksBefore)
        assertThat(TestHelper.findEmails(recipient = inactiveUser.email).size)
            .describedAs("emails to ${inactiveUser.email} after a preview")
            .isEqualTo(emailsBefore)
    }
}
