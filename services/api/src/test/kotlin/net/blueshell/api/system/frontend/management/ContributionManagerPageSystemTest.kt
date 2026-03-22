package net.blueshell.api.system.frontend.management

import com.microsoft.playwright.Page
import net.blueshell.api.domain.contribution.persistence.Contribution
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.contribution.persistence.repository.ContributionRepository
import net.blueshell.api.domain.user.application.erasure.UserErasureService
import net.blueshell.api.factory.contribution.persistence.ContributionFactory
import net.blueshell.api.factory.user.persistence.UserFactory
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.system.frontend.FrontendSystemTestBase
import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.api.system.frontend.helper.ContributionManagerHelper
import net.blueshell.api.system.frontend.helper.ContributionPeriodHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Tag("system")
class ContributionManagerPageSystemTest : FrontendSystemTestBase() {

    @Autowired
    private lateinit var userFactory: UserFactory

    @Autowired
    private lateinit var contributionFactory: ContributionFactory

    @Autowired
    private lateinit var contributionRepository: ContributionRepository

    @Autowired
    private lateinit var erasure: UserErasureService

    @Test
    fun `board adds period and switches paid status between periods`() {
        val board = userFactory.createUserWithRole(Role.BOARD, enabled = true)
        val member = userFactory.createUserWithRole(Role.MEMBER, enabled = true)
        val memberId = checkNotNull(member.id) { "Expected member id" }
        userFactory.createMembership(member)

        val uniqueOffset = System.currentTimeMillis() % 10_000
        val initialStartDate = LocalDate.now().minusDays(300L + uniqueOffset)
        val initialEndDate = initialStartDate.plusDays(30)
        val addedStartDate = LocalDate.now().plusDays(300L + uniqueOffset)
        val addedEndDate = addedStartDate.plusDays(30)

        val initialPeriod = contributionFactory.createPeriod(initialStartDate, initialEndDate)
        contributionRepository.saveAndFlush(
            Contribution(
                user = member,
                contributionPeriod = initialPeriod
            )
        )

        val initialPeriodLabel = ContributionPeriodHelper.label(initialStartDate, initialEndDate)
        val addedPeriodLabel = ContributionPeriodHelper.label(addedStartDate, addedEndDate)

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            ContributionManagerHelper.open(page, frontendUrl)
            waitFor(
                onTimeoutMessage = { "Expected contribution period '$initialPeriodLabel' to be visible" }
            ) {
                page.getByText(initialPeriodLabel, Page.GetByTextOptions().setExact(false)).count() > 0
            }

            val addStatus = ContributionPeriodHelper.createPeriod(page, addedStartDate, addedEndDate)
            assertThat(addStatus).isEqualTo(201)
            waitFor(
                onTimeoutMessage = { "Expected added contribution period '$addedPeriodLabel' to be visible" }
            ) {
                page.getByText(addedPeriodLabel, Page.GetByTextOptions().setExact(false)).count() > 0
            }

            ContributionManagerHelper.selectPeriod(page, initialPeriodLabel)
            ContributionManagerHelper.openSection(page, "paid")
            waitFor(
                onTimeoutMessage = { "Expected ${member.username} to be paid in initial period" }
            ) {
                page.getByText(member.username, Page.GetByTextOptions().setExact(true)).count() > 0
            }
            waitFor(
                onTimeoutMessage = { "Expected mark unpaid action for ${member.username} in initial period" }
            ) {
                page.locator(
                    "[data-testid='contribution-user-toggle-paid-btn-$memberId'][data-contribution-action='mark-unpaid']"
                ).count() > 0
            }

            ContributionManagerHelper.selectPeriod(page, addedPeriodLabel)
            ContributionManagerHelper.openSection(page, "unpaid")
            waitFor(
                onTimeoutMessage = { "Expected ${member.username} to be unpaid in added period" }
            ) {
                page.getByText(member.username, Page.GetByTextOptions().setExact(true)).count() > 0
            }
            waitFor(
                onTimeoutMessage = { "Expected mark paid action for ${member.username} in added period" }
            ) {
                page.locator(
                    "[data-testid='contribution-user-toggle-paid-btn-$memberId'][data-contribution-action='mark-paid']"
                ).count() > 0
            }
        }
    }

    @Test
    fun `board marks contribution paid and unpaid`() {
        val board = userFactory.createUserWithRole(Role.BOARD, enabled = true)
        val member = userFactory.createUserWithRole(Role.MEMBER, enabled = true)
        userFactory.createMembership(member)

        val (startDate, endDate, period) = createFuturePeriod()
        val periodId = checkNotNull(period.id) { "Expected contribution period id" }
        val periodLabel = "${startDate.format(FORMATTER)} - ${endDate.format(FORMATTER)}"
        val memberId = checkNotNull(member.id) { "Expected member id" }

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            ContributionManagerHelper.open(page, frontendUrl)

            waitFor(
                onTimeoutMessage = { "Expected contribution period '$periodLabel' to be visible" }
            ) {
                page.getByText(periodLabel, Page.GetByTextOptions().setExact(false)).count() > 0
            }
            ContributionManagerHelper.selectPeriod(page, periodLabel)

            ContributionManagerHelper.openSection(page, "unpaid")

            waitFor(
                onTimeoutMessage = { "Expected ${member.username} in unpaid contribution list" }
            ) {
                page.getByText(member.username, Page.GetByTextOptions().setExact(true)).count() > 0
            }

            ContributionManagerHelper.searchUser(page, "unpaid", member.username)
            val markPaidResponse = page.waitForResponse({ response ->
                response.request().method() == "POST" &&
                    response.url().contains("/contributions")
            }) {
                ContributionManagerHelper.togglePaidButton(page, memberId)
            }
            assertThat(markPaidResponse.status()).isEqualTo(201)
        }

        waitFor(
            onTimeoutMessage = { "Expected contribution to be persisted for user $memberId and period $periodId" }
        ) {
            contributionRepository.findByIdContributionPeriodId(periodId).any { it.userId == memberId }
        }

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            ContributionManagerHelper.open(page, frontendUrl)

            waitFor(
                onTimeoutMessage = { "Expected contribution period '$periodLabel' to be visible for unmark flow" }
            ) {
                page.getByText(periodLabel, Page.GetByTextOptions().setExact(false)).count() > 0
            }
            ContributionManagerHelper.selectPeriod(page, periodLabel)

            ContributionManagerHelper.openSection(page, "paid")

            waitFor(
                onTimeoutMessage = { "Expected ${member.username} in paid contribution list" }
            ) {
                page.getByText(member.username, Page.GetByTextOptions().setExact(true)).count() > 0
            }

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

    }

    @Test
    fun `deleted member remains visible in contribution manager lists`() {
        val board = userFactory.createUserWithRole(Role.BOARD, enabled = true)
        val member = userFactory.createUserWithRole(Role.MEMBER, enabled = true)
        userFactory.createMembership(member)
        val memberId = checkNotNull(member.id) { "Expected member id" }

        val (startDate, endDate, _) = createFuturePeriod()
        val periodLabel = "${startDate.format(FORMATTER)} - ${endDate.format(FORMATTER)}"

        erasure.deleteUser(memberId)

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            ContributionManagerHelper.open(page, frontendUrl)
            waitFor(
                onTimeoutMessage = { "Expected contribution period '$periodLabel' to be visible" }
            ) {
                page.getByText(periodLabel, Page.GetByTextOptions().setExact(false)).count() > 0
            }
            ContributionManagerHelper.selectPeriod(page, periodLabel)
            ContributionManagerHelper.openSection(page, "unpaid")

            waitFor(
                onTimeoutMessage = {
                    "Expected deleted member $memberId to stay visible in contribution unpaid list as anonymized user"
                }
            ) {
                page.locator("[data-testid='contribution-user-row-$memberId']").count() > 0
            }
        }
    }

    private fun createFuturePeriod(): Triple<LocalDate, LocalDate, ContributionPeriod> {
        val uniqueOffset = System.currentTimeMillis() % 10_000
        val startDate = LocalDate.now().plusDays(1000L + uniqueOffset)
        val endDate = startDate.plusDays(30)
        return Triple(startDate, endDate, contributionFactory.createPeriod(startDate, endDate))
    }

    private companion object {
        val FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        const val DEFAULT_PASSWORD = "Password123!"
    }
}
