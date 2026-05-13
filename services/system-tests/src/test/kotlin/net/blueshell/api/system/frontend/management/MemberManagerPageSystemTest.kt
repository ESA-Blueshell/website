package net.blueshell.api.system.frontend.management

import com.microsoft.playwright.Page
import net.blueshell.api.ApiApplication
import net.blueshell.api.config.TestCleanUpListener
import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.api.system.frontend.helper.ContributionPeriodHelper
import net.blueshell.api.system.frontend.helper.MemberManagerHelper
import net.blueshell.systemtests.PlaywrightTestBase
import net.blueshell.systemtests.TestHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestExecutionListeners
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.function.Predicate

@Tag("system")
@ActiveProfiles("test")
@TestExecutionListeners(
    listeners = [TestCleanUpListener::class],
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS,
)
@SpringBootTest(
    classes = [ApiApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties = ["server.port=8080", "app.jobs.auto-dispatch=true"],
)
class MemberManagerPageSystemTest : PlaywrightTestBase() {

    @Test
    fun `member visibility follows membership period when switching periods`() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        val stableMember = TestHelper.registerActivateAndPromote("MEMBER")
        val periodOnlyMember = TestHelper.registerActivateAndPromote("MEMBER")

        val uniqueOffset = System.currentTimeMillis() % 10_000
        val initialStartDate = LocalDate.now().minusDays(360L + uniqueOffset)
        val initialEndDate = initialStartDate.plusDays(30)
        val addedStartDate = LocalDate.now().plusDays(360L + uniqueOffset)
        val addedEndDate = addedStartDate.plusDays(30)

        TestHelper.attachMembership(
            username = stableMember.username,
            startDate = initialStartDate.minusDays(10),
            endDate = null,
        )
        TestHelper.attachMembership(
            username = periodOnlyMember.username,
            startDate = initialStartDate.minusDays(10),
            endDate = initialEndDate.minusDays(1),
        )
        TestHelper.createContributionPeriod(initialStartDate, initialEndDate)

        val initialLabel = ContributionPeriodHelper.label(initialStartDate, initialEndDate)
        val addedLabel = ContributionPeriodHelper.label(addedStartDate, addedEndDate)

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)

        MemberManagerHelper.open(page, frontendUrl)
        page.getByText(initialLabel, Page.GetByTextOptions().setExact(false)).first().waitFor()

        val addStatus = ContributionPeriodHelper.createPeriod(page, addedStartDate, addedEndDate)
        assertThat(addStatus).isEqualTo(201)
        page.getByText(addedLabel, Page.GetByTextOptions().setExact(false)).first().waitFor()

        selectPeriod(initialLabel)
        MemberManagerHelper.openMembers(page)

        MemberManagerHelper.searchMembers(page, stableMember.username)
        waitUntilAttached(page.getByText(stableMember.username, Page.GetByTextOptions().setExact(true)).first())

        MemberManagerHelper.searchMembers(page, periodOnlyMember.username)
        waitUntilAttached(page.getByText(periodOnlyMember.username, Page.GetByTextOptions().setExact(true)).first())

        selectPeriod(addedLabel)
        MemberManagerHelper.openMembers(page)

        MemberManagerHelper.searchMembers(page, stableMember.username)
        waitUntilAttached(page.getByText(stableMember.username, Page.GetByTextOptions().setExact(true)).first())

        MemberManagerHelper.searchMembers(page, periodOnlyMember.username)
        page.getByText("No users found.", Page.GetByTextOptions().setExact(true)).first().waitFor()
    }

    @Test
    fun `board starts membership for non-member`() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        val guest = TestHelper.registerActivateAndPromote("GUEST")
        val guestId = TestHelper.findUser(guest.username)!!.id
        val periodLabel = createFuturePeriodLabel()

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)

        MemberManagerHelper.open(page, frontendUrl)
        selectPeriod(periodLabel)

        MemberManagerHelper.openNonMembers(page)
        page.getByText(guest.username, Page.GetByTextOptions().setExact(true)).first().waitFor()

        MemberManagerHelper.searchNonMembers(page, guest.username)

        val response = page.waitForResponse(
            Predicate { response ->
                response.request().method() == "POST" &&
                    response.url().contains("/users/$guestId/memberships")
            },
        ) {
            MemberManagerHelper.clickStartMembership(page, guestId)
            page.locator("[data-testid='start-membership-confirm-btn']").first().click()
        }
        assertThat(response.status()).isEqualTo(201)

        pollFor("active membership for $guestId") { TestHelper.hasActiveMembership(guest.username) }
    }

    @Test
    fun `board ends membership`() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        val member = TestHelper.registerActivateAndPromote("MEMBER")
        val membershipId = TestHelper.attachMembership(member.username)
        val memberId = TestHelper.findUser(member.username)!!.id
        val periodLabel = createFuturePeriodLabel()

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)

        MemberManagerHelper.open(page, frontendUrl)
        selectPeriod(periodLabel)

        MemberManagerHelper.openMembers(page)
        page.getByText(member.username, Page.GetByTextOptions().setExact(true)).first().waitFor()

        MemberManagerHelper.searchMembers(page, member.username)

        val endResponse = page.waitForResponse(
            Predicate { response ->
                response.request().method() == "PUT" &&
                    response.url().contains("/memberships/$membershipId")
            },
        ) {
            MemberManagerHelper.clickEndMembership(page, memberId)
        }
        assertThat(endResponse.status()).isEqualTo(200)

        pollFor("membership $membershipId has end date") {
            TestHelper.findMembership(membershipId)?.endDate != null
        }
    }

    @Test
    fun `deleted user stays visible in member manager as anonymized row`() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        val target = TestHelper.registerActivateAndPromote("GUEST")
        val targetId = TestHelper.findUser(target.username)!!.id
        val periodLabel = createFuturePeriodLabel()

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)

        MemberManagerHelper.open(page, frontendUrl)
        selectPeriod(periodLabel)
        MemberManagerHelper.openNonMembers(page)
        MemberManagerHelper.searchNonMembers(page, target.username)
        page.locator("[data-testid='member-user-row-$targetId']").first().waitFor()

        val deleteResponse = page.waitForResponse(
            Predicate { response ->
                response.request().method() == "DELETE" &&
                    response.url().contains("/users/$targetId")
            },
        ) {
            MemberManagerHelper.clickDeleteUser(page, targetId)
            MemberManagerHelper.confirmDelete(page)
        }
        assertThat(deleteResponse.status()).isEqualTo(204)

        MemberManagerHelper.open(page, frontendUrl)
        selectPeriod(periodLabel)
        MemberManagerHelper.openNonMembers(page)

        page.locator("[data-testid='member-user-row-$targetId']").first().waitFor()
    }

    private fun createFuturePeriodLabel(): String {
        val uniqueOffset = System.currentTimeMillis() % 1_000
        val startDate = LocalDate.now().plusDays(900L + uniqueOffset)
        val endDate = startDate.plusDays(60)
        TestHelper.createContributionPeriod(startDate, endDate)
        return "${startDate.format(FORMATTER)} - ${endDate.format(FORMATTER)}"
    }

    private fun selectPeriod(periodLabel: String) {
        page.getByText(periodLabel, Page.GetByTextOptions().setExact(false)).first().waitFor()
        page.getByText(periodLabel, Page.GetByTextOptions().setExact(false)).first().click()
    }

    private fun waitUntilAttached(locator: com.microsoft.playwright.Locator) {
        locator.waitFor(
            com.microsoft.playwright.Locator.WaitForOptions()
                .setState(com.microsoft.playwright.options.WaitForSelectorState.ATTACHED),
        )
    }

    private fun pollFor(description: String, timeoutMs: Long = 10_000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            Thread.sleep(200)
        }
        throw AssertionError("Expected '$description' within ${timeoutMs}ms")
    }

    private companion object {
        val FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    }
}
