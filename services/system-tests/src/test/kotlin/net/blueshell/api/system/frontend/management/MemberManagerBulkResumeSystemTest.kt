package net.blueshell.api.system.frontend.management

import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.api.system.frontend.helper.MemberManagerBulkHelper
import net.blueshell.systemtests.PlaywrightTestBase
import net.blueshell.systemtests.TestHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * System tests for the member-manager bulk "resume / start membership" action.
 *
 * Covers:
 * - Resume: latest membership ended within most-recent period → endDate cleared.
 * - Start-new: latest membership ended before basis period → new membership today copying type/incasso.
 * - No prior: user has no membership → REGULAR/incasso=false inserted.
 * - Already-active: user has active membership → SKIPPED.
 */
@Tag("system")
class MemberManagerBulkResumeSystemTest : PlaywrightTestBase() {

    /** Create a contribution period centred on today so membership dates are predictable. */
    private fun createRecentPeriod(): Long = TestHelper.createContributionPeriod(
        startDate = LocalDate.now().minusDays(15),
        endDate = LocalDate.now().plusDays(345),
    )

    @Test
    fun `resume clears endDate for membership that ended within basis period`() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        val uniqueOffset = System.currentTimeMillis() % 10_000
        val periodId = createRecentPeriod()

        val member = TestHelper.registerActivateAndPromote(
            "MEMBER",
            username = "resume_inperiod_${uniqueOffset}",
        )
        // Membership ended within basis period (5 days ago)
        val membershipId = TestHelper.attachMembership(
            member.username,
            memberType = "REGULAR",
            startDate = LocalDate.now().minusDays(100),
            endDate = LocalDate.now().minusDays(5),
            incasso = false,
        )
        val memberId = TestHelper.findUser(member.username)!!.id

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)

        MemberManagerBulkHelper.openMemberManager(page, frontendUrl)
        MemberManagerBulkHelper.selectPeriod(page, periodId)
        MemberManagerBulkHelper.selectUserRow(page, memberId)
        MemberManagerBulkHelper.openBulkMenu(page)
        MemberManagerBulkHelper.chooseAction(page, "resume-membership")
        MemberManagerBulkHelper.waitForDialog(page)

        // Preview: row should be INCLUDED with outcome WILL_RESUME
        assertThat(MemberManagerBulkHelper.dispositionOf(page, memberId)).isEqualTo("INCLUDED")
        val note = MemberManagerBulkHelper.reasonOf(page, memberId)
        assertThat(note).containsIgnoringCase("resume")

        MemberManagerBulkHelper.confirm(page)
        MemberManagerBulkHelper.waitForSuccess(page)

        // Backend: endDate must be null now
        val membership = TestHelper.findMembership(membershipId)
        assertThat(membership).isNotNull
        assertThat(membership!!.endDate).isNull()
    }

    @Test
    fun `start-new inserts membership today copying memberType and incasso from prior`() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        val uniqueOffset = System.currentTimeMillis() % 10_000
        createRecentPeriod()

        val member = TestHelper.registerActivateAndPromote(
            "MEMBER",
            username = "resume_startnew_${uniqueOffset}",
        )
        // Membership ended well before basis period
        TestHelper.attachMembership(
            member.username,
            memberType = "ALUMNI",
            startDate = LocalDate.now().minusDays(400),
            endDate = LocalDate.now().minusDays(200),
            incasso = true,
        )
        val memberId = TestHelper.findUser(member.username)!!.id

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)

        MemberManagerBulkHelper.openMemberManager(page, frontendUrl)
        MemberManagerBulkHelper.selectUserRow(page, memberId)
        MemberManagerBulkHelper.openBulkMenu(page)
        MemberManagerBulkHelper.chooseAction(page, "resume-membership")
        MemberManagerBulkHelper.waitForDialog(page)

        assertThat(MemberManagerBulkHelper.dispositionOf(page, memberId)).isEqualTo("INCLUDED")
        val note = MemberManagerBulkHelper.reasonOf(page, memberId)
        assertThat(note).containsIgnoringCase("start new")

        MemberManagerBulkHelper.confirm(page)
        MemberManagerBulkHelper.waitForSuccess(page)

        // Backend: a new membership starting today with ALUMNI/incasso=true
        val allMemberships = TestHelper.findMembershipsForUser(memberId)
        assertThat(allMemberships).hasSize(2)
        val newMembership = allMemberships
            .map { TestHelper.findMembership(it)!! }
            .first { it.endDate == null }
        // The API container runs in Europe/Amsterdam (TZ set in docker-compose.ci.yml),
        // so the server stamps the new membership with the Amsterdam date. Assert against
        // that same zone rather than the test JVM's default (UTC), which otherwise diverges
        // by a day when the run straddles midnight Amsterdam.
        assertThat(newMembership.startDate).isEqualTo(LocalDate.now(ZoneId.of("Europe/Amsterdam")))
        assertThat(newMembership.type).isEqualTo("ALUMNI")
        assertThat(newMembership.incasso).isTrue()
    }

    @Test
    fun `start-new with no prior membership inserts REGULAR non-incasso membership`() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        val uniqueOffset = System.currentTimeMillis() % 10_000
        createRecentPeriod()

        val member = TestHelper.registerActivateAndPromote(
            "MEMBER",
            username = "resume_noprior_${uniqueOffset}",
        )
        val memberId = TestHelper.findUser(member.username)!!.id

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)

        MemberManagerBulkHelper.openMemberManager(page, frontendUrl)
        MemberManagerBulkHelper.selectUserRow(page, memberId)
        MemberManagerBulkHelper.openBulkMenu(page)
        MemberManagerBulkHelper.chooseAction(page, "resume-membership")
        MemberManagerBulkHelper.waitForDialog(page)

        assertThat(MemberManagerBulkHelper.dispositionOf(page, memberId)).isEqualTo("INCLUDED")

        MemberManagerBulkHelper.confirm(page)
        MemberManagerBulkHelper.waitForSuccess(page)

        val allMemberships = TestHelper.findMembershipsForUser(memberId)
        assertThat(allMemberships).hasSize(1)
        val newMembership = TestHelper.findMembership(allMemberships.first())!!
        // The API container runs in Europe/Amsterdam (TZ set in docker-compose.ci.yml),
        // so the server stamps the new membership with the Amsterdam date. Assert against
        // that same zone rather than the test JVM's default (UTC), which otherwise diverges
        // by a day when the run straddles midnight Amsterdam.
        assertThat(newMembership.startDate).isEqualTo(LocalDate.now(ZoneId.of("Europe/Amsterdam")))
        assertThat(newMembership.type).isEqualTo("REGULAR")
        assertThat(newMembership.incasso).isFalse()
    }

    @Test
    fun `already-active membership is skipped`() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        val uniqueOffset = System.currentTimeMillis() % 10_000
        createRecentPeriod()

        val member = TestHelper.registerActivateAndPromote(
            "MEMBER",
            username = "resume_active_${uniqueOffset}",
        )
        TestHelper.attachMembership(
            member.username,
            endDate = null, // active
        )
        val memberId = TestHelper.findUser(member.username)!!.id

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)

        MemberManagerBulkHelper.openMemberManager(page, frontendUrl)
        MemberManagerBulkHelper.selectUserRow(page, memberId)
        MemberManagerBulkHelper.openBulkMenu(page)
        MemberManagerBulkHelper.chooseAction(page, "resume-membership")
        MemberManagerBulkHelper.waitForDialog(page)

        assertThat(MemberManagerBulkHelper.dispositionOf(page, memberId)).isEqualTo("SKIPPED")
        val note = MemberManagerBulkHelper.reasonOf(page, memberId)
        assertThat(note).containsIgnoringCase("active")
    }
}
