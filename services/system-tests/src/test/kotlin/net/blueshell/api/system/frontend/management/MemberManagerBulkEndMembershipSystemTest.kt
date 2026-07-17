package net.blueshell.api.system.frontend.management

import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.api.system.frontend.helper.MemberManagerBulkHelper
import net.blueshell.systemtests.PlaywrightTestBase
import net.blueshell.systemtests.TestHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * System tests for the member-manager bulk "end membership" action.
 * Verifies that memberships are end-dated correctly, and that already-ended
 * memberships are skipped/excluded.
 */
@Tag("system")
class MemberManagerBulkEndMembershipSystemTest : PlaywrightTestBase() {

    @Test
    fun `end membership applies to active memberships across a selection`() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        val uniqueOffset = System.currentTimeMillis() % 10_000

        // Create three members with active memberships
        val member1 = TestHelper.registerActivateAndPromote(
            "MEMBER",
            username = "end_mem_1_${uniqueOffset}",
        )
        TestHelper.attachMembership(
            member1.username,
            memberType = "REGULAR",
            startDate = java.time.LocalDate.now().minusDays(100),
            endDate = null,
        )
        val member1Id = TestHelper.findUser(member1.username)!!.id

        val member2 = TestHelper.registerActivateAndPromote(
            "MEMBER",
            username = "end_mem_2_${uniqueOffset}",
        )
        TestHelper.attachMembership(
            member2.username,
            memberType = "ALUMNI",
            startDate = java.time.LocalDate.now().minusDays(80),
            endDate = null,
        )
        val member2Id = TestHelper.findUser(member2.username)!!.id

        // Create a member with an already-ended membership
        val member3 = TestHelper.registerActivateAndPromote(
            "MEMBER",
            username = "end_mem_3_${uniqueOffset}",
        )
        val endedDate = java.time.LocalDate.now().minusDays(10)
        TestHelper.attachMembership(
            member3.username,
            memberType = "REGULAR",
            startDate = java.time.LocalDate.now().minusDays(200),
            endDate = endedDate,
        )
        val member3Id = TestHelper.findUser(member3.username)!!.id

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)

        MemberManagerBulkHelper.openMemberManager(page, frontendUrl)

        // Select all three members
        MemberManagerBulkHelper.selectUserRow(page, member1Id)
        MemberManagerBulkHelper.selectUserRow(page, member2Id)
        MemberManagerBulkHelper.selectUserRow(page, member3Id)

        // Open bulk actions menu and choose end-membership
        MemberManagerBulkHelper.openBulkMenu(page)
        MemberManagerBulkHelper.chooseAction(page, "end-membership")

        MemberManagerBulkHelper.waitForDialog(page)

        // member1 and member2 should be INCLUDED
        assertThat(MemberManagerBulkHelper.dispositionOf(page, member1Id))
            .isEqualTo("INCLUDED")
        assertThat(MemberManagerBulkHelper.dispositionOf(page, member2Id))
            .isEqualTo("INCLUDED")

        // member3 (already ended) should be SKIPPED or EXCLUDED
        val member3Disposition = MemberManagerBulkHelper.dispositionOf(page, member3Id)
        assertThat(member3Disposition).isIn("SKIPPED", "EXCLUDED")

        // Execute
        MemberManagerBulkHelper.confirm(page)
        MemberManagerBulkHelper.waitForSuccess(page)

        // Verify memberships were end-dated (check they still exist with end_date set)
        val member1Membership = TestHelper.findMembership(
            TestHelper.findMembershipsForUser(member1Id).first(),
        )
        assertThat(member1Membership).isNotNull
        assertThat(member1Membership!!.endDate).isNotNull

        val member2Membership = TestHelper.findMembership(
            TestHelper.findMembershipsForUser(member2Id).first(),
        )
        assertThat(member2Membership).isNotNull
        assertThat(member2Membership!!.endDate).isNotNull

        // Verify member3's membership was not modified (still has the same end date)
        val member3Membership = TestHelper.findMembership(
            TestHelper.findMembershipsForUser(member3Id).first(),
        )
        assertThat(member3Membership).isNotNull
        assertThat(member3Membership!!.endDate).isEqualTo(endedDate)
    }

    @Test
    fun `member without active membership is excluded`() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        val uniqueOffset = System.currentTimeMillis() % 10_000

        // Create a member with no active membership
        val member = TestHelper.registerActivateAndPromote(
            "MEMBER",
            username = "no_membership_${uniqueOffset}",
        )
        val memberId = TestHelper.findUser(member.username)!!.id

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)

        MemberManagerBulkHelper.openMemberManager(page, frontendUrl)
        MemberManagerBulkHelper.selectUserRow(page, memberId)

        MemberManagerBulkHelper.openBulkMenu(page)
        MemberManagerBulkHelper.chooseAction(page, "end-membership")

        MemberManagerBulkHelper.waitForDialog(page)

        // Should be EXCLUDED or SKIPPED
        val disposition = MemberManagerBulkHelper.dispositionOf(page, memberId)
        assertThat(disposition).isIn("EXCLUDED", "SKIPPED")
    }
}
