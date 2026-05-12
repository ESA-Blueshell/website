package net.blueshell.api.system.frontend.management

import com.microsoft.playwright.Page
import net.blueshell.api.ApiApplication
import net.blueshell.api.config.TestCleanUpListener
import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.api.system.frontend.helper.ContributionManagerHelper
import net.blueshell.api.system.frontend.helper.ContributionPeriodHelper
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
class ContributionManagerPageSystemTest : PlaywrightTestBase() {

    @Test
    fun `board adds period and switches paid status between periods`() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        val member = TestHelper.registerActivateAndPromote("MEMBER")
        TestHelper.attachMembership(member.username)
        val memberId = TestHelper.findUser(member.username)!!.id

        val uniqueOffset = System.currentTimeMillis() % 10_000
        val initialStartDate = LocalDate.now().minusDays(300L + uniqueOffset)
        val initialEndDate = initialStartDate.plusDays(30)
        val addedStartDate = LocalDate.now().plusDays(300L + uniqueOffset)
        val addedEndDate = addedStartDate.plusDays(30)

        val initialPeriodId = TestHelper.createContributionPeriod(initialStartDate, initialEndDate)
        TestHelper.createContribution(initialPeriodId, member.username)

        val initialPeriodLabel = ContributionPeriodHelper.label(initialStartDate, initialEndDate)
        val addedPeriodLabel = ContributionPeriodHelper.label(addedStartDate, addedEndDate)

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)

        ContributionManagerHelper.open(page, frontendUrl)
        page.getByText(initialPeriodLabel, Page.GetByTextOptions().setExact(false)).first().waitFor()

        val addStatus = ContributionPeriodHelper.createPeriod(page, addedStartDate, addedEndDate)
        assertThat(addStatus).isEqualTo(201)
        page.getByText(addedPeriodLabel, Page.GetByTextOptions().setExact(false)).first().waitFor()

        ContributionManagerHelper.selectPeriod(page, initialPeriodLabel)
        ContributionManagerHelper.openSection(page, "paid")
        page.getByText(member.username, Page.GetByTextOptions().setExact(true)).first().waitFor()
        page.locator(
            "[data-testid='contribution-user-toggle-paid-btn-$memberId'][data-contribution-action='mark-unpaid']",
        ).first().waitFor()

        ContributionManagerHelper.selectPeriod(page, addedPeriodLabel)
        ContributionManagerHelper.openSection(page, "unpaid")
        page.getByText(member.username, Page.GetByTextOptions().setExact(true)).first().waitFor()
        page.locator(
            "[data-testid='contribution-user-toggle-paid-btn-$memberId'][data-contribution-action='mark-paid']",
        ).first().waitFor()
    }

    @Test
    fun `board marks contribution paid and unpaid`() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        val member = TestHelper.registerActivateAndPromote("MEMBER")
        TestHelper.attachMembership(member.username)
        val memberId = TestHelper.findUser(member.username)!!.id

        val (startDate, endDate, periodId) = createFuturePeriod()
        val periodLabel = "${startDate.format(FORMATTER)} - ${endDate.format(FORMATTER)}"

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)

        ContributionManagerHelper.open(page, frontendUrl)
        page.getByText(periodLabel, Page.GetByTextOptions().setExact(false)).first().waitFor()
        ContributionManagerHelper.selectPeriod(page, periodLabel)
        ContributionManagerHelper.openSection(page, "unpaid")
        page.getByText(member.username, Page.GetByTextOptions().setExact(true)).first().waitFor()

        ContributionManagerHelper.searchUser(page, "unpaid", member.username)
        val markPaidResponse = page.waitForResponse({ response ->
            response.request().method() == "POST" &&
                response.url().contains("/contributions")
        }) {
            ContributionManagerHelper.togglePaidButton(page, memberId)
        }
        assertThat(markPaidResponse.status()).isEqualTo(201)

        pollFor("contribution persisted for user=$memberId period=$periodId") {
            memberId in TestHelper.findContributions(periodId)
        }

        // Second page navigation: mark unpaid via DELETE.
        page.navigate("$frontendUrl/")
        AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        ContributionManagerHelper.open(page, frontendUrl)
        page.getByText(periodLabel, Page.GetByTextOptions().setExact(false)).first().waitFor()
        ContributionManagerHelper.selectPeriod(page, periodLabel)
        ContributionManagerHelper.openSection(page, "paid")
        page.getByText(member.username, Page.GetByTextOptions().setExact(true)).first().waitFor()

        ContributionManagerHelper.searchUser(page, "paid", member.username)
        val markUnpaidResponse = page.waitForResponse({ response ->
            response.request().method() == "DELETE" &&
                response.url().contains("/contributionPeriods/") &&
                response.url().contains("/contributions")
        }) {
            ContributionManagerHelper.togglePaidButton(page, memberId)
        }
        assertThat(markUnpaidResponse.status()).isEqualTo(204)
    }

    @Test
    fun `deleted member remains visible in contribution manager lists`() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        val member = TestHelper.registerActivateAndPromote("MEMBER")
        TestHelper.attachMembership(member.username)
        val memberId = TestHelper.findUser(member.username)!!.id

        val (startDate, endDate, _) = createFuturePeriod()
        val periodLabel = "${startDate.format(FORMATTER)} - ${endDate.format(FORMATTER)}"

        TestHelper.eraseUser(member.username)

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, board.password)
        assertThat(loginStatus).isEqualTo(200)

        ContributionManagerHelper.open(page, frontendUrl)
        page.getByText(periodLabel, Page.GetByTextOptions().setExact(false)).first().waitFor()
        ContributionManagerHelper.selectPeriod(page, periodLabel)
        ContributionManagerHelper.openSection(page, "unpaid")
        page.locator("[data-testid='contribution-user-row-$memberId']").first().waitFor()
    }

    private fun createFuturePeriod(): Triple<LocalDate, LocalDate, Long> {
        val uniqueOffset = System.currentTimeMillis() % 10_000
        val startDate = LocalDate.now().plusDays(1000L + uniqueOffset)
        val endDate = startDate.plusDays(30)
        return Triple(startDate, endDate, TestHelper.createContributionPeriod(startDate, endDate))
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
