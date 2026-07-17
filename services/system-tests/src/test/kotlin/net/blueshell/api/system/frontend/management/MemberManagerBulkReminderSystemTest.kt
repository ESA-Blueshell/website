package net.blueshell.api.system.frontend.management

import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.api.system.frontend.helper.MemberManagerBulkHelper
import net.blueshell.api.system.frontend.helper.TestIdLocatorHelper
import net.blueshell.systemtests.PlaywrightTestBase
import net.blueshell.systemtests.TestHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * System tests for the member-manager bulk "send reminder" action.
 * Verifies the full backend→frontend orchestration: correct per-user
 * dispositions + fee amounts computed by the real backend and rendered
 * in the preview dialog.
 */
@Tag("system")
class MemberManagerBulkReminderSystemTest : PlaywrightTestBase() {

    @Test
    fun `reminder preview renders correct dispositions and fees for a mixed cohort`() {
        // Create a BOARD user to log in as
        val board = TestHelper.registerActivateAndPromote("BOARD")

        // Create a unique offset to avoid test collisions across parallel runs
        val uniqueOffset = System.currentTimeMillis() % 10_000

        // Create a contribution period with distinct half-year, full-year, and alumni fees
        val periodStart = LocalDate.now().minusDays(60L + uniqueOffset)
        val periodEnd = LocalDate.now().plusDays(300L + uniqueOffset)
        val halfYearFee = 25.00
        val fullYearFee = 50.00
        val alumniFee = 15.00
        val periodId = TestHelper.createContributionPeriod(
            periodStart,
            periodEnd,
            halfYearFee = halfYearFee,
            fullYearFee = fullYearFee,
            alumniFee = alumniFee,
        )

        // Set the cutoff date: halfway through the period
        val cutoffDate = periodStart.plusDays((periodEnd.toEpochDay() - periodStart.toEpochDay()) / 2)

        // Create 5 members with different scenarios:
        // 1. Regular member started before cutoff => full-year fee (INCLUDED)
        val regularBeforeCutoff = TestHelper.registerActivateAndPromote(
            "MEMBER",
            username = "regular_before_${uniqueOffset}",
        )
        TestHelper.attachMembership(
            regularBeforeCutoff.username,
            memberType = "REGULAR",
            startDate = periodStart.minusDays(30),
        )
        val regularBeforeId = TestHelper.findUser(regularBeforeCutoff.username)!!.id

        // 2. Regular member started on/after cutoff => half-year fee (INCLUDED)
        val regularAfterCutoff = TestHelper.registerActivateAndPromote(
            "MEMBER",
            username = "regular_after_${uniqueOffset}",
        )
        TestHelper.attachMembership(
            regularAfterCutoff.username,
            memberType = "REGULAR",
            startDate = cutoffDate.plusDays(5),
        )
        val regularAfterId = TestHelper.findUser(regularAfterCutoff.username)!!.id

        // 3. Alumni member => alumni fee (INCLUDED)
        val alumniMember = TestHelper.registerActivateAndPromote(
            "MEMBER",
            username = "alumni_${uniqueOffset}",
        )
        TestHelper.attachMembership(
            alumniMember.username,
            memberType = "ALUMNI",
            startDate = periodStart.minusDays(10),
        )
        val alumniId = TestHelper.findUser(alumniMember.username)!!.id

        // 4. Honorary member => no fee (EXCLUDED)
        val honoryMember = TestHelper.registerActivateAndPromote(
            "MEMBER",
            username = "honorary_${uniqueOffset}",
        )
        TestHelper.attachMembership(
            honoryMember.username,
            memberType = "HONORARY",
            startDate = periodStart.minusDays(10),
        )
        val honoraryId = TestHelper.findUser(honoryMember.username)!!.id

        // 5. Regular member already paid => WARNING
        val alreadyPaidMember = TestHelper.registerActivateAndPromote(
            "MEMBER",
            username = "paid_${uniqueOffset}",
        )
        TestHelper.attachMembership(
            alreadyPaidMember.username,
            memberType = "REGULAR",
            startDate = periodStart.minusDays(20),
        )
        val alreadyPaidId = TestHelper.findUser(alreadyPaidMember.username)!!.id
        // Mark as already contributed for this period
        TestHelper.createContribution(periodId, alreadyPaidMember.username)

        // Log in as BOARD
        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)

        // Open member manager
        MemberManagerBulkHelper.openMemberManager(page, frontendUrl)
        MemberManagerBulkHelper.selectPeriod(page, periodId)

        // Select all 5 members
        MemberManagerBulkHelper.selectUserRow(page, regularBeforeId)
        MemberManagerBulkHelper.selectUserRow(page, regularAfterId)
        MemberManagerBulkHelper.selectUserRow(page, alumniId)
        MemberManagerBulkHelper.selectUserRow(page, honoraryId)
        MemberManagerBulkHelper.selectUserRow(page, alreadyPaidId)

        // Open bulk actions menu and choose send-reminder
        MemberManagerBulkHelper.openBulkMenu(page)
        MemberManagerBulkHelper.chooseAction(page, "send-reminder")

        // Wait for the dialog to load
        MemberManagerBulkHelper.waitForDialog(page)

        // Set the cutoff date (determines half-year vs full-year fee)
        MemberManagerBulkHelper.setCutoffDate(page, formatDate(cutoffDate))

        // Set the payment due date
        val paymentDueDate = periodEnd.plusDays(14)
        MemberManagerBulkHelper.setPaymentDueDate(page, formatDate(paymentDueDate))

        // Assert dispositions and fees for each member
        // 1. Regular before cutoff => INCLUDED with full-year fee
        assertThat(MemberManagerBulkHelper.dispositionOf(page, regularBeforeId))
            .isEqualTo("INCLUDED")
        val regularBeforeFeeText = MemberManagerBulkHelper.amountOf(page, regularBeforeId)
        assertThat(regularBeforeFeeText.toDouble()).isEqualTo(fullYearFee)

        // 2. Regular after cutoff => INCLUDED with half-year fee
        assertThat(MemberManagerBulkHelper.dispositionOf(page, regularAfterId))
            .isEqualTo("INCLUDED")
        val regularAfterFeeText = MemberManagerBulkHelper.amountOf(page, regularAfterId)
        assertThat(regularAfterFeeText.toDouble()).isEqualTo(halfYearFee)

        // 3. Alumni => INCLUDED with alumni fee
        assertThat(MemberManagerBulkHelper.dispositionOf(page, alumniId))
            .isEqualTo("INCLUDED")
        val alumniFeeText = MemberManagerBulkHelper.amountOf(page, alumniId)
        assertThat(alumniFeeText.toDouble()).isEqualTo(alumniFee)

        // 4. Honorary => EXCLUDED
        assertThat(MemberManagerBulkHelper.dispositionOf(page, honoraryId))
            .isEqualTo("EXCLUDED")
        val honoraryReason = MemberManagerBulkHelper.reasonOf(page, honoraryId)
        assertThat(honoraryReason).isEqualTo("Honorary — no contribution needed")

        // 5. Already paid => WARNING
        assertThat(MemberManagerBulkHelper.dispositionOf(page, alreadyPaidId))
            .isEqualTo("WARNING")
        val paidReason = MemberManagerBulkHelper.reasonOf(page, alreadyPaidId)
        assertThat(paidReason).isEqualTo("Already paid")

        // Verify bulk-action-counts: 3 included (regular before, regular after, alumni),
        // 1 excluded (honorary), 1 warning (already paid)
        val countsElement = TestIdLocatorHelper.byTestId(page, "bulk-action-counts")
        val countsText = countsElement.textContent()
        assertThat(countsText).contains("3") // 3 included
    }

    @Test
    fun `re-including the already-paid warning and executing succeeds`() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        val uniqueOffset = System.currentTimeMillis() % 10_000

        val periodStart = LocalDate.now().minusDays(45L + uniqueOffset)
        val periodEnd = LocalDate.now().plusDays(300L + uniqueOffset)
        val periodId = TestHelper.createContributionPeriod(
            periodStart,
            periodEnd,
            halfYearFee = 30.00,
            fullYearFee = 60.00,
            alumniFee = 20.00,
        )

        // Create a regular member and mark them as already paid
        val member = TestHelper.registerActivateAndPromote(
            "MEMBER",
            username = "already_paid_reinclude_${uniqueOffset}",
        )
        TestHelper.attachMembership(member.username, memberType = "REGULAR", startDate = periodStart.minusDays(10))
        val memberId = TestHelper.findUser(member.username)!!.id
        TestHelper.createContribution(periodId, member.username)

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)

        MemberManagerBulkHelper.openMemberManager(page, frontendUrl)
        MemberManagerBulkHelper.selectPeriod(page, periodId)
        MemberManagerBulkHelper.selectUserRow(page, memberId)

        MemberManagerBulkHelper.openBulkMenu(page)
        MemberManagerBulkHelper.chooseAction(page, "send-reminder")
        MemberManagerBulkHelper.waitForDialog(page)

        val cutoffDate = periodStart.plusDays(30)
        MemberManagerBulkHelper.setCutoffDate(page, formatDate(cutoffDate))
        MemberManagerBulkHelper.setPaymentDueDate(page, formatDate(periodEnd.plusDays(14)))

        // Verify initially WARNING
        assertThat(MemberManagerBulkHelper.dispositionOf(page, memberId))
            .isEqualTo("WARNING")

        // Re-include the WARNING member
        MemberManagerBulkHelper.toggleReInclude(page, memberId)

        // After re-include, disposition should change to INCLUDED
        assertThat(MemberManagerBulkHelper.dispositionOf(page, memberId))
            .isEqualTo("INCLUDED")

        // Execute the action
        MemberManagerBulkHelper.confirm(page)
        MemberManagerBulkHelper.waitForSuccess(page)

        // Verify the action completed
    }

    @Test
    fun `fee type override is reflected`() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        val uniqueOffset = System.currentTimeMillis() % 10_000

        val periodStart = LocalDate.now().minusDays(50L + uniqueOffset)
        val periodEnd = LocalDate.now().plusDays(300L + uniqueOffset)
        val fullYearFee = 45.00
        val halfYearFee = 22.50
        val periodId = TestHelper.createContributionPeriod(
            periodStart,
            periodEnd,
            halfYearFee = halfYearFee,
            fullYearFee = fullYearFee,
            alumniFee = 15.00,
        )

        // Member started before cutoff → default recommended: FULL_YEAR_FEE
        val member = TestHelper.registerActivateAndPromote(
            "MEMBER",
            username = "fee_type_override_${uniqueOffset}",
        )
        TestHelper.attachMembership(member.username, memberType = "REGULAR", startDate = periodStart.minusDays(10))
        val memberId = TestHelper.findUser(member.username)!!.id

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)

        MemberManagerBulkHelper.openMemberManager(page, frontendUrl)
        MemberManagerBulkHelper.selectPeriod(page, periodId)
        MemberManagerBulkHelper.selectUserRow(page, memberId)

        MemberManagerBulkHelper.openBulkMenu(page)
        MemberManagerBulkHelper.chooseAction(page, "send-reminder")
        MemberManagerBulkHelper.waitForDialog(page)

        val cutoffDate = periodStart.plusDays(30)
        MemberManagerBulkHelper.setCutoffDate(page, formatDate(cutoffDate))
        MemberManagerBulkHelper.setPaymentDueDate(page, formatDate(periodEnd.plusDays(14)))

        // Initial state: should show INCLUDED with FULL_YEAR_FEE recommended
        assertThat(MemberManagerBulkHelper.dispositionOf(page, memberId))
            .isEqualTo("INCLUDED")

        // Override the fee type to HALF_YEAR_FEE
        MemberManagerBulkHelper.chooseFeeType(page, memberId, "HALF_YEAR_FEE")

        // Verify the selection is reflected (label shows "Half-year fee")
        val selectedLabel = MemberManagerBulkHelper.selectedFeeTypeLabel(page, memberId)
        assertThat(selectedLabel).contains("Half-year fee")

        // Execute the action
        MemberManagerBulkHelper.confirm(page)
        MemberManagerBulkHelper.waitForSuccess(page)
    }

    private fun formatDate(date: LocalDate): String {
        return FORMATTER.format(date)
    }

    private companion object {
        val FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
}
