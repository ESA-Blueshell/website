package net.blueshell.api.system.frontend.management

import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.api.system.frontend.helper.MemberManagerBulkHelper
import net.blueshell.api.system.frontend.helper.MemberManagerHelper
import net.blueshell.api.system.frontend.helper.TestIdLocatorHelper
import net.blueshell.systemtests.PlaywrightTestBase
import net.blueshell.systemtests.TestHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * System tests for the member-manager bulk selection UI.
 * Verifies selection persistence across filters, select-all toggle, menu
 * enable/disable, and row-body click toggle behavior.
 */
@Tag("system")
class MemberManagerBulkSelectionSystemTest : PlaywrightTestBase() {

    @Test
    fun `selection persists across search filter changes`() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        val uniqueOffset = System.currentTimeMillis() % 10_000

        // Create two members with distinct usernames
        val member1 = TestHelper.registerActivateAndPromote(
            "MEMBER",
            username = "select_alice_${uniqueOffset}",
        )
        TestHelper.attachMembership(member1.username)
        val member1Id = TestHelper.findUser(member1.username)!!.id

        val member2 = TestHelper.registerActivateAndPromote(
            "MEMBER",
            username = "select_bob_${uniqueOffset}",
        )
        TestHelper.attachMembership(member2.username)
        val member2Id = TestHelper.findUser(member2.username)!!.id

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)

        MemberManagerBulkHelper.openMemberManager(page, frontendUrl)

        // Select member 1
        MemberManagerBulkHelper.selectUserRow(page, member1Id)
        assertThat(MemberManagerBulkHelper.isRowSelected(page, member1Id)).isTrue

        // Search for member 1's name (should still show as selected)
        MemberManagerHelper.search(page, "alice")
        Thread.sleep(500) // Wait for filter to apply
        assertThat(MemberManagerBulkHelper.isRowSelected(page, member1Id)).isTrue

        // Clear search and select member 2
        MemberManagerHelper.search(page, "")
        Thread.sleep(500)
        MemberManagerBulkHelper.selectUserRow(page, member2Id)
        assertThat(MemberManagerBulkHelper.isRowSelected(page, member1Id)).isTrue
        assertThat(MemberManagerBulkHelper.isRowSelected(page, member2Id)).isTrue

        // Search again (both should remain selected)
        MemberManagerHelper.search(page, "bob")
        Thread.sleep(500)
        assertThat(MemberManagerBulkHelper.isRowSelected(page, member2Id)).isTrue
        // member1 is filtered out but selection should persist
    }

    @Test
    fun `select-all-displayed toggles only shown rows`() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        val uniqueOffset = System.currentTimeMillis() % 10_000

        // Create three members
        val member1 = TestHelper.registerActivateAndPromote(
            "MEMBER",
            username = "all_charlie_${uniqueOffset}",
        )
        TestHelper.attachMembership(member1.username)
        val member1Id = TestHelper.findUser(member1.username)!!.id

        val member2 = TestHelper.registerActivateAndPromote(
            "MEMBER",
            username = "all_diana_${uniqueOffset}",
        )
        TestHelper.attachMembership(member2.username)
        val member2Id = TestHelper.findUser(member2.username)!!.id

        val member3 = TestHelper.registerActivateAndPromote(
            "MEMBER",
            username = "all_emma_${uniqueOffset}",
        )
        TestHelper.attachMembership(member3.username)
        val member3Id = TestHelper.findUser(member3.username)!!.id

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)

        MemberManagerBulkHelper.openMemberManager(page, frontendUrl)

        // Click select-all to select all visible rows
        MemberManagerBulkHelper.selectAllDisplayed(page)
        assertThat(MemberManagerBulkHelper.isRowSelected(page, member1Id)).isTrue
        assertThat(MemberManagerBulkHelper.isRowSelected(page, member2Id)).isTrue
        assertThat(MemberManagerBulkHelper.isRowSelected(page, member3Id)).isTrue

        // Search to filter down to one member
        MemberManagerHelper.search(page, "emma")
        Thread.sleep(500)

        // Click select-all again to deselect all visible rows
        MemberManagerBulkHelper.selectAllDisplayed(page)
        assertThat(MemberManagerBulkHelper.isRowSelected(page, member3Id)).isFalse
        // Other members' selections should persist (they are just not visible)
    }

    @Test
    fun `bulk menu is disabled with no selection and enabled after selecting`() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        val uniqueOffset = System.currentTimeMillis() % 10_000

        val member = TestHelper.registerActivateAndPromote(
            "MEMBER",
            username = "menu_test_${uniqueOffset}",
        )
        TestHelper.attachMembership(member.username)
        val memberId = TestHelper.findUser(member.username)!!.id

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)

        MemberManagerBulkHelper.openMemberManager(page, frontendUrl)

        // Menu button should be disabled initially
        val menuBtn = TestIdLocatorHelper.byTestId(page, "bulk-actions-menu-btn")
        assertThat(menuBtn.isDisabled).isTrue

        // Select a row
        MemberManagerBulkHelper.selectUserRow(page, memberId)

        // Menu button should now be enabled
        assertThat(menuBtn.isDisabled).isFalse
    }

    @Test
    fun `row-body click toggles row selection when selection is active`() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        val uniqueOffset = System.currentTimeMillis() % 10_000

        // Create two members
        val member1 = TestHelper.registerActivateAndPromote(
            "MEMBER",
            username = "rowclick_frank_${uniqueOffset}",
        )
        TestHelper.attachMembership(member1.username)
        val member1Id = TestHelper.findUser(member1.username)!!.id

        val member2 = TestHelper.registerActivateAndPromote(
            "MEMBER",
            username = "rowclick_grace_${uniqueOffset}",
        )
        TestHelper.attachMembership(member2.username)
        val member2Id = TestHelper.findUser(member2.username)!!.id

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)

        MemberManagerBulkHelper.openMemberManager(page, frontendUrl)

        // With no selection, row-body click should be a no-op.
        // The table is virtualized: scroll each row into the render window
        // before clicking, since off-screen rows are not in the DOM.
        MemberManagerBulkHelper.scrollRowIntoView(page, member1Id)
        val rowBody1 = TestIdLocatorHelper.byTestId(page, "member-manager-row-$member1Id")
        rowBody1.click()
        assertThat(MemberManagerBulkHelper.isRowSelected(page, member1Id)).isFalse

        // Select member1 via checkbox (now selection is active)
        MemberManagerBulkHelper.selectUserRow(page, member1Id)
        assertThat(MemberManagerBulkHelper.isRowSelected(page, member1Id)).isTrue

        // Row-body click on member2 should toggle selection
        MemberManagerBulkHelper.scrollRowIntoView(page, member2Id)
        val rowBody2 = TestIdLocatorHelper.byTestId(page, "member-manager-row-$member2Id")
        rowBody2.click()
        assertThat(MemberManagerBulkHelper.isRowSelected(page, member2Id)).isTrue

        // Row-body click on member1 should deselect
        MemberManagerBulkHelper.scrollRowIntoView(page, member1Id)
        rowBody1.click()
        assertThat(MemberManagerBulkHelper.isRowSelected(page, member1Id)).isFalse

        // Verify the bulk-actions menu is enabled (member2 remains selected)
        val menuBtn = TestIdLocatorHelper.byTestId(page, "bulk-actions-menu-btn")
        assertThat(menuBtn.isDisabled).isFalse
    }

    @Test
    fun `row-body click is no-op when nothing is selected`() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        val uniqueOffset = System.currentTimeMillis() % 10_000

        val member = TestHelper.registerActivateAndPromote(
            "MEMBER",
            username = "rowclick_noop_${uniqueOffset}",
        )
        TestHelper.attachMembership(member.username)
        val memberId = TestHelper.findUser(member.username)!!.id

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)

        MemberManagerBulkHelper.openMemberManager(page, frontendUrl)

        // No selection active
        assertThat(MemberManagerBulkHelper.isRowSelected(page, memberId)).isFalse

        // Click row body (scroll the virtualized table until the row exists)
        MemberManagerBulkHelper.scrollRowIntoView(page, memberId)
        val rowBody = TestIdLocatorHelper.byTestId(page, "member-manager-row-$memberId")
        rowBody.click()

        // Should still be not selected
        assertThat(MemberManagerBulkHelper.isRowSelected(page, memberId)).isFalse
    }
}
