package net.blueshell.api.system.frontend.management

import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.api.system.frontend.helper.MemberManagerBulkHelper
import net.blueshell.systemtests.PlaywrightTestBase
import net.blueshell.systemtests.TestHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * System tests for the member-manager bulk "mark paid" / "mark unpaid" actions.
 * Verifies the full backend→frontend orchestration: contributions are created
 * on mark-paid, deleted on mark-unpaid, and UI reflects the changes.
 */
@Tag("system")
class MemberManagerBulkMarkPaidSystemTest : PlaywrightTestBase() {

    @Test
    fun `mark paid creates contributions and mark unpaid removes them`() {
        // Create a BOARD user
        val board = TestHelper.registerActivateAndPromote("BOARD")

        // Create a unique offset to avoid test collisions
        val uniqueOffset = System.currentTimeMillis() % 10_000

        // Create a contribution period
        val periodStart = java.time.LocalDate.now().minusDays(60L + uniqueOffset)
        val periodEnd = java.time.LocalDate.now().plusDays(300L + uniqueOffset)
        val periodId = TestHelper.createContributionPeriod(
            periodStart,
            periodEnd,
            halfYearFee = 25.00,
            fullYearFee = 50.00,
            alumniFee = 15.00,
        )

        // Create two regular members
        val member1 = TestHelper.registerActivateAndPromote(
            "MEMBER",
            username = "mark_paid_1_${uniqueOffset}",
        )
        TestHelper.attachMembership(
            member1.username,
            memberType = "REGULAR",
            startDate = periodStart.minusDays(20),
        )
        val member1Id = TestHelper.findUser(member1.username)!!.id

        val member2 = TestHelper.registerActivateAndPromote(
            "MEMBER",
            username = "mark_paid_2_${uniqueOffset}",
        )
        TestHelper.attachMembership(
            member2.username,
            memberType = "REGULAR",
            startDate = periodStart.minusDays(15),
        )
        val member2Id = TestHelper.findUser(member2.username)!!.id

        // Log in as BOARD
        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)

        // Open member manager
        MemberManagerBulkHelper.openMemberManager(page, frontendUrl)
        MemberManagerBulkHelper.selectPeriod(page, periodId)

        // Select both members
        MemberManagerBulkHelper.selectUserRow(page, member1Id)
        MemberManagerBulkHelper.selectUserRow(page, member2Id)

        // Open bulk actions menu and choose mark-paid
        MemberManagerBulkHelper.openBulkMenu(page)
        MemberManagerBulkHelper.chooseAction(page, "mark-paid")

        // Wait for the dialog
        MemberManagerBulkHelper.waitForDialog(page)

        // Both should be INCLUDED
        assertThat(MemberManagerBulkHelper.dispositionOf(page, member1Id))
            .isEqualTo("INCLUDED")
        assertThat(MemberManagerBulkHelper.dispositionOf(page, member2Id))
            .isEqualTo("INCLUDED")

        // Execute mark-paid
        MemberManagerBulkHelper.confirm(page)
        MemberManagerBulkHelper.waitForSuccess(page)

        // Verify contributions were created
        val contributions = TestHelper.findContributions(periodId)
        assertThat(contributions).containsExactlyInAnyOrder(member1Id, member2Id)

        // The mark-unpaid preview is computed from the page's paidUserIds, which the host
        // refreshes only after the mark-paid success. Wait for the rows to show "Paid"
        // before opening the next dialog so it does not race the in-flight reload and
        // classify everything as NOT_PAID (which would leave nothing to include).
        MemberManagerBulkHelper.waitForPaidStatus(page, member1Id, "Paid")
        MemberManagerBulkHelper.waitForPaidStatus(page, member2Id, "Paid")

        // Now mark them as unpaid
        MemberManagerBulkHelper.selectUserRow(page, member1Id)
        MemberManagerBulkHelper.selectUserRow(page, member2Id)

        MemberManagerBulkHelper.openBulkMenu(page)
        MemberManagerBulkHelper.chooseAction(page, "mark-unpaid")

        MemberManagerBulkHelper.waitForDialog(page)

        // Execute mark-unpaid
        MemberManagerBulkHelper.confirm(page)
        MemberManagerBulkHelper.waitForSuccess(page)

        // Verify contributions were removed
        val contributionsAfterUnpaid = TestHelper.findContributions(periodId)
        assertThat(contributionsAfterUnpaid).isEmpty()
    }

    @Test
    fun `already paid member is skipped when marking paid`() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        val uniqueOffset = System.currentTimeMillis() % 10_000

        val periodStart = java.time.LocalDate.now().minusDays(50L + uniqueOffset)
        val periodEnd = java.time.LocalDate.now().plusDays(300L + uniqueOffset)
        val periodId = TestHelper.createContributionPeriod(
            periodStart,
            periodEnd,
            halfYearFee = 25.00,
            fullYearFee = 50.00,
            alumniFee = 15.00,
        )

        // Create a member and mark them as already paid
        val member = TestHelper.registerActivateAndPromote(
            "MEMBER",
            username = "already_paid_${uniqueOffset}",
        )
        TestHelper.attachMembership(
            member.username,
            memberType = "REGULAR",
            startDate = periodStart.minusDays(20),
        )
        val memberId = TestHelper.findUser(member.username)!!.id
        TestHelper.createContribution(periodId, member.username)

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)

        MemberManagerBulkHelper.openMemberManager(page, frontendUrl)
        MemberManagerBulkHelper.selectPeriod(page, periodId)
        MemberManagerBulkHelper.selectUserRow(page, memberId)

        MemberManagerBulkHelper.openBulkMenu(page)
        MemberManagerBulkHelper.chooseAction(page, "mark-paid")

        MemberManagerBulkHelper.waitForDialog(page)

        // Marking an already-paid member as paid is a no-op: the row is SKIPPED,
        // not a re-includable warning (there is nothing to apply).
        assertThat(MemberManagerBulkHelper.dispositionOf(page, memberId))
            .isEqualTo("SKIPPED")

        // The note still explains why it was skipped.
        val reason = MemberManagerBulkHelper.reasonOf(page, memberId)
        assertThat(reason).isEqualTo("Already paid")
    }
}
