package net.blueshell.api.system.frontend.management

import com.microsoft.playwright.Page
import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.api.system.frontend.helper.MemberManagerBulkHelper
import net.blueshell.api.system.frontend.helper.TestIdLocatorHelper
import net.blueshell.systemtests.PlaywrightTestBase
import net.blueshell.systemtests.TestHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.function.Predicate

/**
 * System tests for member-manager bulk actions authorization.
 * Verifies that non-BOARD users cannot access the member manager or
 * bulk actions, and that the confirm button is disabled when all rows
 * are excluded.
 */
@Tag("system")
class MemberManagerBulkAuthzSystemTest : PlaywrightTestBase() {

    @Test
    fun `non-board user cannot access member manager`() {
        // Create a non-BOARD user (MEMBER role)
        val member = TestHelper.registerActivateAndPromote("MEMBER")

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, member.username, member.password)
        assertThat(loginStatus).isEqualTo(200)

        // The member manager lives at /members/manage and loads its data from the
        // BOARD-only list endpoint GET /api/users. A non-BOARD user reaching the page
        // receives a 401/403 on that call, so no member data loads.
        val response = page.waitForResponse(
            Predicate { response ->
                response.url().contains("/users") && response.status() >= 400
            },
        ) {
            page.navigate("$frontendUrl/members/manage")
        }
        assertThat(response.status()).isIn(401, 403)

        // With the list call rejected, no member rows render.
        val rowCount = page.locator("[data-testid^='member-manager-row-']").count()
        assertThat(rowCount).isZero()
    }

    @Test
    fun `non-board user cannot open bulk actions dialog`() {
        // Create both BOARD and non-BOARD users
        val board = TestHelper.registerActivateAndPromote("BOARD")
        val nonBoard = TestHelper.registerActivateAndPromote("MEMBER")

        val member = TestHelper.registerActivateAndPromote(
            "MEMBER",
            username = "authz_test_member",
        )
        TestHelper.attachMembership(member.username)

        // Log in as BOARD first to set up
        val boardLoginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(boardLoginStatus).isEqualTo(200)

        MemberManagerBulkHelper.openMemberManager(page, frontendUrl)
        val memberId = TestHelper.findUser(member.username)!!.id
        MemberManagerBulkHelper.selectUserRow(page, memberId)

        // Log out
        page.navigate("$frontendUrl/logout")
        Thread.sleep(500)

        // Log in as non-BOARD
        val nonBoardLoginStatus = AuthHelper.submitLogin(page, frontendUrl, nonBoard.username, nonBoard.password)
        assertThat(nonBoardLoginStatus).isEqualTo(200)

        // As a non-BOARD user the member-manager list call (GET /api/users) is
        // rejected, so the bulk actions can never be reached: no member rows load.
        val response = page.waitForResponse(
            Predicate { response ->
                response.url().contains("/users") && response.status() >= 400
            },
        ) {
            page.navigate("$frontendUrl/members/manage")
        }
        assertThat(response.status()).isIn(401, 403)

        val rowCount = page.locator("[data-testid^='member-manager-row-']").count()
        assertThat(rowCount).isZero()
    }

    @Test
    fun `confirm button is disabled when all rows are excluded`() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        val uniqueOffset = System.currentTimeMillis() % 10_000

        val periodStart = java.time.LocalDate.now().minusDays(60L + uniqueOffset)
        val periodEnd = java.time.LocalDate.now().plusDays(300L + uniqueOffset)
        TestHelper.createContributionPeriod(
            periodStart,
            periodEnd,
            halfYearFee = 25.00,
            fullYearFee = 50.00,
            alumniFee = 15.00,
        )

        // Create only honorary members (will be excluded for send-reminder)
        val honorary1 = TestHelper.registerActivateAndPromote(
            "MEMBER",
            username = "authz_honorary_1_${uniqueOffset}",
        )
        TestHelper.attachMembership(
            honorary1.username,
            memberType = "HONORARY",
            startDate = periodStart.minusDays(20),
        )
        val honorary1Id = TestHelper.findUser(honorary1.username)!!.id

        val honorary2 = TestHelper.registerActivateAndPromote(
            "MEMBER",
            username = "authz_honorary_2_${uniqueOffset}",
        )
        TestHelper.attachMembership(
            honorary2.username,
            memberType = "HONORARY",
            startDate = periodStart.minusDays(15),
        )
        val honorary2Id = TestHelper.findUser(honorary2.username)!!.id

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)

        MemberManagerBulkHelper.openMemberManager(page, frontendUrl)

        // Select both honorary members
        MemberManagerBulkHelper.selectUserRow(page, honorary1Id)
        MemberManagerBulkHelper.selectUserRow(page, honorary2Id)

        // Open bulk actions menu and choose send-reminder
        MemberManagerBulkHelper.openBulkMenu(page)
        MemberManagerBulkHelper.chooseAction(page, "send-reminder")

        MemberManagerBulkHelper.waitForDialog(page)

        // Set the required dates
        val paymentDueDate = periodEnd.plusDays(14)
        MemberManagerBulkHelper.setPaymentDueDate(page, formatDate(paymentDueDate))

        // Both honorary members should be EXCLUDED
        assertThat(MemberManagerBulkHelper.dispositionOf(page, honorary1Id))
            .isEqualTo("EXCLUDED")
        assertThat(MemberManagerBulkHelper.dispositionOf(page, honorary2Id))
            .isEqualTo("EXCLUDED")

        // Confirm button should be disabled (no includable rows)
        val confirmBtn = TestIdLocatorHelper.byTestId(page, "bulk-action-confirm-btn")
        assertThat(confirmBtn.isDisabled).isTrue
    }

    private fun formatDate(date: java.time.LocalDate): String {
        return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd").format(date)
    }
}
