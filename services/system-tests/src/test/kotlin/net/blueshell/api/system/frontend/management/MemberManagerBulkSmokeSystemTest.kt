package net.blueshell.api.system.frontend.management

import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.api.system.frontend.helper.MemberManagerBulkHelper
import net.blueshell.systemtests.PlaywrightTestBase
import net.blueshell.systemtests.TestHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("system")
class MemberManagerBulkSmokeSystemTest : PlaywrightTestBase() {

    @Test
    fun `board opens member manager bulk menu and sees all 5 action items`() {
        // Create a BOARD user to log in as
        val board = TestHelper.registerActivateAndPromote("BOARD")

        // Create two regular members (with unique offsets to avoid test collision)
        val uniqueOffset1 = System.currentTimeMillis() % 10_000
        val uniqueOffset2 = (System.currentTimeMillis() + 1) % 10_000

        val member1 = TestHelper.registerActivateAndPromote(
            "MEMBER",
            username = "member_${uniqueOffset1}",
        )
        val member2 = TestHelper.registerActivateAndPromote(
            "MEMBER",
            username = "member_${uniqueOffset2}",
        )

        // Attach memberships to make them visible in the member manager
        TestHelper.attachMembership(member1.username)
        TestHelper.attachMembership(member2.username)

        val member1Id = TestHelper.findUser(member1.username)!!.id
        val member2Id = TestHelper.findUser(member2.username)!!.id

        // Log in as BOARD
        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)

        // Open member manager
        MemberManagerBulkHelper.openMemberManager(page, frontendUrl)

        // Select both member rows
        MemberManagerBulkHelper.selectUserRow(page, member1Id)
        MemberManagerBulkHelper.selectUserRow(page, member2Id)

        // Verify both rows are selected
        assertThat(MemberManagerBulkHelper.isRowSelected(page, member1Id)).isTrue
        assertThat(MemberManagerBulkHelper.isRowSelected(page, member2Id)).isTrue

        // Open bulk actions menu
        MemberManagerBulkHelper.openBulkMenu(page)

        // Verify all 5 action menu items are visible and accessible
        val menuActions = listOf(
            "bulk-action-mark-paid",
            "bulk-action-mark-unpaid",
            "bulk-action-send-reminder",
            "bulk-action-send-incasso",
            "bulk-action-end-membership",
        )

        for (actionTestId in menuActions) {
            val actionItem = page.locator("[data-testid='$actionTestId']").first()
            assertThat(actionItem.isVisible).isTrue
                .withFailMessage("Action item $actionTestId should be visible in bulk menu")
        }
    }
}
