package net.blueshell.api.system.frontend.management

import com.microsoft.playwright.Page
import net.blueshell.api.domain.user.persistence.repository.DeletedUserRepository
import net.blueshell.api.factory.user.persistence.UserFactory
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.system.frontend.FrontendSystemTestBase
import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.api.system.frontend.helper.MemberManagerHelper
import net.blueshell.api.system.frontend.helper.RecoveryManagerHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.function.Predicate

@Tag("system")
class RecoveryManagerPageSystemTest : FrontendSystemTestBase() {

    @Autowired
    private lateinit var userFactory: UserFactory

    @Autowired
    private lateinit var deletedUsers: DeletedUserRepository

    @Test
    fun `recovery manager resends activation for inactive user`() {
        val board = userFactory.createUserWithRole(Role.BOARD, enabled = true)
        val inactiveUser = userFactory.createUserWithRole(Role.GUEST, enabled = false)

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            RecoveryManagerHelper.open(page, frontendUrl)

            RecoveryManagerHelper.openSection(page, "inactive")
            RecoveryManagerHelper.searchUser(page, "inactive", inactiveUser.username)

            waitFor(
                onTimeoutMessage = { "Expected inactive user ${inactiveUser.username} to be visible" }
            ) {
                RecoveryManagerHelper.rowCount(page, "inactive", inactiveUser.id!!) > 0
            }

            val response = page.waitForResponse("**/recovery/user/activate/resend/**") {
                RecoveryManagerHelper.clickAction(page, "activation", inactiveUser.id!!)
            }
            assertThat(response.status()).isEqualTo(204)
        }

        assertEmailSent(inactiveUser.email, "Activate your Account")
    }

    @Test
    fun `recovery manager sends password reset for active user`() {
        val board = userFactory.createUserWithRole(Role.BOARD, enabled = true)
        val activeUser = userFactory.createUserWithRole(Role.GUEST, enabled = true)

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            RecoveryManagerHelper.open(page, frontendUrl)

            RecoveryManagerHelper.openSection(page, "active")
            RecoveryManagerHelper.searchUser(page, "active", activeUser.username)

            waitFor(
                onTimeoutMessage = { "Expected active user ${activeUser.username} to be visible" }
            ) {
                RecoveryManagerHelper.rowCount(page, "active", activeUser.id!!) > 0
            }

            val response = page.waitForResponse("**/recovery/password/reset/**") {
                RecoveryManagerHelper.clickAction(page, "password", activeUser.id!!)
            }
            assertThat(response.status()).isEqualTo(204)
        }

        assertEmailSent(activeUser.email, "Reset Your Blueshell Account Password")
    }

    @Test
    fun `board deletes user from member manager and restores in recovery manager`() {
        val board = userFactory.createUserWithRole(Role.BOARD, enabled = true)
        val target = userFactory.createUserWithRole(Role.GUEST, enabled = true)
        val targetId = checkNotNull(target.id) { "Expected target user id" }
        val originalUsername = target.username
        val originalEmail = target.email

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            MemberManagerHelper.open(page, frontendUrl)
            MemberManagerHelper.openNonMembers(page)
            MemberManagerHelper.searchNonMembers(page, originalUsername)

            waitFor(
                onTimeoutMessage = { "Expected non-member user $originalUsername to be visible before deletion" }
            ) {
                page.locator("[data-testid='member-user-row-$targetId']").count() > 0
            }

            val deleteResponse = page.waitForResponse(
                Predicate { response ->
                    response.request().method() == "DELETE" && response.url().contains("/users/$targetId")
                }
            ) {
                MemberManagerHelper.clickDeleteUser(page, targetId)
                MemberManagerHelper.confirmDelete(page)
            }
            assertThat(deleteResponse.status()).isEqualTo(204)

            waitFor(
                onTimeoutMessage = { "Expected user row $targetId to disappear from non-members after deletion" }
            ) {
                page.locator("[data-testid='member-user-row-$targetId']").count() == 0
            }

            RecoveryManagerHelper.open(page, frontendUrl)
            RecoveryManagerHelper.openSection(page, "deleted")
            RecoveryManagerHelper.searchUser(page, "deleted", originalUsername)

            waitFor(
                onTimeoutMessage = { "Expected deleted user $originalUsername to appear in deleted recovery pane" }
            ) {
                RecoveryManagerHelper.rowCount(page, "deleted", targetId) > 0
            }

            val restoreResponse = page.waitForResponse(
                Predicate { response ->
                    response.request().method() == "PUT" && response.url().contains("/users/$targetId/restore")
                }
            ) {
                RecoveryManagerHelper.clickAction(page, "restore", targetId)
            }
            assertThat(restoreResponse.status()).isEqualTo(204)

            waitFor(
                onTimeoutMessage = { "Expected deleted recovery row for user $targetId to be removed after restore" }
            ) {
                RecoveryManagerHelper.rowCount(page, "deleted", targetId) == 0
            }

            RecoveryManagerHelper.openSection(page, "active")
            RecoveryManagerHelper.searchUser(page, "active", originalUsername)

            waitFor(
                onTimeoutMessage = { "Expected restored user $originalUsername to appear in active recovery pane" }
            ) {
                RecoveryManagerHelper.rowCount(page, "active", targetId) > 0
            }
        }

        waitFor(
            onTimeoutMessage = { "Expected deleted snapshot to be removed and user restored in persistence layer" }
        ) {
            val restored = userRepository.findById(targetId).orElse(null)
            restored != null &&
                restored.username == originalUsername &&
                restored.email == originalEmail &&
                deletedUsers.findById(targetId).isEmpty
        }
    }

    @Test
    fun `deleted user remains visible in inactive pane while also present in deleted pane`() {
        val board = userFactory.createUserWithRole(Role.BOARD, enabled = true)
        val target = userFactory.createUserWithRole(Role.GUEST, enabled = true)
        val targetId = checkNotNull(target.id) { "Expected target user id" }

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            MemberManagerHelper.open(page, frontendUrl)
            MemberManagerHelper.openNonMembers(page)
            MemberManagerHelper.searchNonMembers(page, target.username)

            waitFor(
                onTimeoutMessage = { "Expected target user ${target.username} to be visible before deletion" }
            ) {
                page.locator("[data-testid='member-user-row-$targetId']").count() > 0
            }

            val deleteResponse = page.waitForResponse(
                Predicate { response ->
                    response.request().method() == "DELETE" && response.url().contains("/users/$targetId")
                }
            ) {
                MemberManagerHelper.clickDeleteUser(page, targetId)
                MemberManagerHelper.confirmDelete(page)
            }
            assertThat(deleteResponse.status()).isEqualTo(204)

            RecoveryManagerHelper.open(page, frontendUrl)

            RecoveryManagerHelper.openSection(page, "deleted")
            waitFor(
                onTimeoutMessage = { "Expected deleted user $targetId in deleted pane" }
            ) {
                RecoveryManagerHelper.rowCount(page, "deleted", targetId) > 0
            }

            RecoveryManagerHelper.openSection(page, "inactive")
            waitFor(
                onTimeoutMessage = {
                    "Expected deleted user $targetId to remain visible in inactive pane as anonymized user"
                }
            ) {
                RecoveryManagerHelper.rowCount(page, "inactive", targetId) > 0
            }
        }
    }

    private companion object {
        const val DEFAULT_PASSWORD = "Password123!"
    }
}
