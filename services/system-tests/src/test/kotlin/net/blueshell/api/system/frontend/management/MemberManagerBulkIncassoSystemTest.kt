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
 * System tests for the member-manager bulk "send incasso notification" action.
 * Verifies correct disposition and reason labeling for mixed cohorts with
 * incasso flag mismatches and member type distinctions.
 */
@Tag("system")
class MemberManagerBulkIncassoSystemTest : PlaywrightTestBase() {

    @Test
    fun `incasso preview shows correct dispositions and fees for a mixed cohort`() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        val uniqueOffset = System.currentTimeMillis() % 10_000

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

        val cutoffDate = periodStart.plusDays((periodEnd.toEpochDay() - periodStart.toEpochDay()) / 2)

        // 1. Regular member with incasso=true => INCLUDED with regular fee
        val regularWithIncasso = TestHelper.registerActivateAndPromote(
            "MEMBER",
            username = "incasso_regular_yes_${uniqueOffset}",
        )
        TestHelper.attachMembership(
            regularWithIncasso.username,
            memberType = "REGULAR",
            startDate = periodStart.minusDays(30),
            incasso = true,
        )
        val regularWithIncassoId = TestHelper.findUser(regularWithIncasso.username)!!.id

        // 2. Regular member with incasso=false => WARNING "Not marked for incasso"
        val regularNoIncasso = TestHelper.registerActivateAndPromote(
            "MEMBER",
            username = "incasso_regular_no_${uniqueOffset}",
        )
        TestHelper.attachMembership(
            regularNoIncasso.username,
            memberType = "REGULAR",
            startDate = periodStart.minusDays(25),
            incasso = false,
        )
        val regularNoIncassoId = TestHelper.findUser(regularNoIncasso.username)!!.id

        // 3. Honorary member => EXCLUDED "Honorary — no contribution needed"
        val honoryMember = TestHelper.registerActivateAndPromote(
            "MEMBER",
            username = "incasso_honorary_${uniqueOffset}",
        )
        TestHelper.attachMembership(
            honoryMember.username,
            memberType = "HONORARY",
            startDate = periodStart.minusDays(20),
            incasso = true,
        )
        val honoraryId = TestHelper.findUser(honoryMember.username)!!.id

        // 4. Alumni member with incasso=true => INCLUDED with alumni fee
        val alumniWithIncasso = TestHelper.registerActivateAndPromote(
            "MEMBER",
            username = "incasso_alumni_yes_${uniqueOffset}",
        )
        TestHelper.attachMembership(
            alumniWithIncasso.username,
            memberType = "ALUMNI",
            startDate = periodStart.minusDays(40),
            incasso = true,
        )
        val alumniWithIncassoId = TestHelper.findUser(alumniWithIncasso.username)!!.id

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)

        MemberManagerBulkHelper.openMemberManager(page, frontendUrl)
        MemberManagerBulkHelper.selectPeriod(page, periodId)

        // Select all 4 members
        MemberManagerBulkHelper.selectUserRow(page, regularWithIncassoId)
        MemberManagerBulkHelper.selectUserRow(page, regularNoIncassoId)
        MemberManagerBulkHelper.selectUserRow(page, honoraryId)
        MemberManagerBulkHelper.selectUserRow(page, alumniWithIncassoId)

        MemberManagerBulkHelper.openBulkMenu(page)
        MemberManagerBulkHelper.chooseAction(page, "send-incasso")

        MemberManagerBulkHelper.waitForDialog(page)

        val incassoDate = periodEnd.plusDays(10)
        MemberManagerBulkHelper.setExpectedIncassoDate(page, formatDate(incassoDate))
        MemberManagerBulkHelper.setCutoffDate(page, formatDate(cutoffDate))

        // 1. Regular with incasso=true => INCLUDED
        assertThat(MemberManagerBulkHelper.dispositionOf(page, regularWithIncassoId))
            .isEqualTo("INCLUDED")
        val regularWithIncassoFee = MemberManagerBulkHelper.amountOf(page, regularWithIncassoId)
        assertThat(regularWithIncassoFee.toDouble()).isEqualTo(fullYearFee)

        // 2. Regular with incasso=false => WARNING with reason "Not marked for incasso"
        assertThat(MemberManagerBulkHelper.dispositionOf(page, regularNoIncassoId))
            .isEqualTo("WARNING")
        val regularNoIncassoReason = MemberManagerBulkHelper.reasonOf(page, regularNoIncassoId)
        assertThat(regularNoIncassoReason).isEqualTo("Not marked for incasso")

        // 3. Honorary => EXCLUDED with reason
        assertThat(MemberManagerBulkHelper.dispositionOf(page, honoraryId))
            .isEqualTo("EXCLUDED")
        val honoraryReason = MemberManagerBulkHelper.reasonOf(page, honoraryId)
        assertThat(honoraryReason).isEqualTo("Honorary — no contribution needed")

        // 4. Alumni with incasso=true => INCLUDED with alumni fee
        assertThat(MemberManagerBulkHelper.dispositionOf(page, alumniWithIncassoId))
            .isEqualTo("INCLUDED")
        val alumniFeeText = MemberManagerBulkHelper.amountOf(page, alumniWithIncassoId)
        assertThat(alumniFeeText.toDouble()).isEqualTo(alumniFee)

        // Verify counts: 2 included (regular+incasso, alumni), 1 warning (regular no incasso), 1 excluded (honorary)
        val countsElement = TestIdLocatorHelper.byTestId(page, "bulk-action-counts")
        val countsText = countsElement.textContent()
        assertThat(countsText).contains("2") // 2 included
    }

    @Test
    fun `re-including non-incasso member and executing succeeds`() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        val uniqueOffset = System.currentTimeMillis() % 10_000

        val periodStart = LocalDate.now().minusDays(50L + uniqueOffset)
        val periodEnd = LocalDate.now().plusDays(300L + uniqueOffset)
        val periodId = TestHelper.createContributionPeriod(
            periodStart,
            periodEnd,
            halfYearFee = 25.00,
            fullYearFee = 50.00,
            alumniFee = 15.00,
        )

        // Create a regular member without incasso flag
        val member = TestHelper.registerActivateAndPromote(
            "MEMBER",
            username = "incasso_reinclude_${uniqueOffset}",
        )
        TestHelper.attachMembership(
            member.username,
            memberType = "REGULAR",
            startDate = periodStart.minusDays(20),
            incasso = false,
        )
        val memberId = TestHelper.findUser(member.username)!!.id

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)

        MemberManagerBulkHelper.openMemberManager(page, frontendUrl)
        MemberManagerBulkHelper.selectPeriod(page, periodId)
        MemberManagerBulkHelper.selectUserRow(page, memberId)

        MemberManagerBulkHelper.openBulkMenu(page)
        MemberManagerBulkHelper.chooseAction(page, "send-incasso")
        MemberManagerBulkHelper.waitForDialog(page)

        val incassoDate = periodEnd.plusDays(10)
        MemberManagerBulkHelper.setExpectedIncassoDate(page, formatDate(incassoDate))
        MemberManagerBulkHelper.setCutoffDate(page, formatDate(periodStart.plusDays(30)))

        // Initially WARNING
        assertThat(MemberManagerBulkHelper.dispositionOf(page, memberId))
            .isEqualTo("WARNING")

        // Re-include
        MemberManagerBulkHelper.toggleReInclude(page, memberId)

        // Now INCLUDED
        assertThat(MemberManagerBulkHelper.dispositionOf(page, memberId))
            .isEqualTo("INCLUDED")

        // Execute
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
