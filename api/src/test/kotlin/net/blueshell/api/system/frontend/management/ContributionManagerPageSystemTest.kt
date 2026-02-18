package net.blueshell.api.system.frontend.management

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat as assertPw
import net.blueshell.api.domain.contribution.persistence.Contribution
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.contribution.persistence.repository.ContributionRepository
import net.blueshell.api.factory.contribution.persistence.ContributionFactory
import net.blueshell.api.factory.user.persistence.UserFactory
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.system.frontend.FrontendSystemTestBase
import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.api.system.frontend.helper.ContributionManagerHelper
import net.blueshell.api.system.frontend.helper.UserListHelper
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

            ContributionManagerHelper.openSection(page, "Contribution unpaid")

            waitFor(
                onTimeoutMessage = { "Expected ${member.username} in unpaid contribution list" }
            ) {
                page.getByText(member.username, Page.GetByTextOptions().setExact(true)).count() > 0
            }

            UserListHelper.searchUser(page, member.username)
            val markPaidResponse = page.waitForResponse({ response ->
                response.request().method() == "POST" &&
                    response.url().contains("/contributions")
            }) {
                page.getByRole(
                    AriaRole.BUTTON,
                    Page.GetByRoleOptions().setName("Mark paid").setExact(false)
                ).first().click()
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

            ContributionManagerHelper.openSection(page, "Contribution paid")

            waitFor(
                onTimeoutMessage = { "Expected ${member.username} in paid contribution list" }
            ) {
                page.getByText(member.username, Page.GetByTextOptions().setExact(true)).count() > 0
            }

            UserListHelper.searchUser(page, member.username)
            val markUnpaidResponse = page.waitForResponse({ response ->
                response.request().method() == "DELETE" &&
                    response.url().contains("/contributionPeriods/") &&
                    response.url().contains("/contributions")
            }) {
                page.getByRole(
                    AriaRole.BUTTON,
                    Page.GetByRoleOptions().setName("Mark unpaid").setExact(false)
                ).first().click()
            }
            assertThat(markUnpaidResponse.status()).isEqualTo(204)
        }

    }

    @Test
    fun `board filters unpaid members by multiple fields`() {
        val board = userFactory.createUserWithRole(Role.BOARD, enabled = true)
        val suffix = System.currentTimeMillis().toString().takeLast(6)
        val target = userFactory.createUserWithRole(Role.MEMBER, enabled = true).apply {
            firstName = "Unpaid$suffix"
            lastName = "Filter"
            discord = "unpaid-filter-$suffix"
        }
        userRepository.saveAndFlush(target)
        userFactory.createMembership(target)

        val other = userFactory.createUserWithRole(Role.MEMBER, enabled = true).apply {
            firstName = "Other$suffix"
            lastName = "Filter"
            discord = "other-filter-$suffix"
        }
        userRepository.saveAndFlush(other)
        userFactory.createMembership(other)

        val (startDate, endDate, _) = createFuturePeriod()
        val periodLabel = "${startDate.format(FORMATTER)} - ${endDate.format(FORMATTER)}"

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            ContributionManagerHelper.open(page, frontendUrl)
            waitFor(
                onTimeoutMessage = { "Expected contribution period '$periodLabel' for unpaid filter test" }
            ) {
                page.getByText(periodLabel, Page.GetByTextOptions().setExact(false)).count() > 0
            }
            ContributionManagerHelper.selectPeriod(page, periodLabel)
            ContributionManagerHelper.openSection(page, "Contribution unpaid")

            waitFor(
                onTimeoutMessage = { "Expected both unpaid users to be visible before filtering" }
            ) {
                page.getByText(target.username, Page.GetByTextOptions().setExact(true)).count() > 0 &&
                    page.getByText(other.username, Page.GetByTextOptions().setExact(true)).count() > 0
            }

            UserListHelper.searchUser(page, "${target.firstName} ${target.discord}")

            waitFor(
                onTimeoutMessage = { "Expected filtered unpaid user ${target.username} to remain visible" }
            ) {
                page.getByText(target.username, Page.GetByTextOptions().setExact(true)).count() > 0
            }
            assertPw(page.getByText(other.username, Page.GetByTextOptions().setExact(true))).hasCount(0)
        }
    }

    @Test
    fun `board filters paid members by multiple fields`() {
        val board = userFactory.createUserWithRole(Role.BOARD, enabled = true)
        val suffix = System.currentTimeMillis().toString().takeLast(6)
        val target = userFactory.createUserWithRole(Role.MEMBER, enabled = true).apply {
            firstName = "TargetPaid$suffix"
            lastName = "Filter"
            discord = "target-paid-filter-$suffix"
        }
        userRepository.saveAndFlush(target)
        userFactory.createMembership(target)

        val other = userFactory.createUserWithRole(Role.MEMBER, enabled = true).apply {
            firstName = "OtherMember$suffix"
            lastName = "Filter"
            discord = "other-filter-$suffix"
        }
        userRepository.saveAndFlush(other)
        userFactory.createMembership(other)

        val (startDate, endDate, period) = createFuturePeriod()
        contributionRepository.saveAndFlush(
            Contribution(
                user = target,
                contributionPeriod = period
            )
        )
        contributionRepository.saveAndFlush(
            Contribution(
                user = other,
                contributionPeriod = period
            )
        )

        val periodLabel = "${startDate.format(FORMATTER)} - ${endDate.format(FORMATTER)}"

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            ContributionManagerHelper.open(page, frontendUrl)
            waitFor(
                onTimeoutMessage = { "Expected contribution period '$periodLabel' for paid filter test" }
            ) {
                page.getByText(periodLabel, Page.GetByTextOptions().setExact(false)).count() > 0
            }
            ContributionManagerHelper.selectPeriod(page, periodLabel)
            ContributionManagerHelper.openSection(page, "Contribution paid")

            waitFor(
                onTimeoutMessage = { "Expected both paid users to be visible before filtering" }
            ) {
                page.getByText(target.username, Page.GetByTextOptions().setExact(true)).count() > 0 &&
                    page.getByText(other.username, Page.GetByTextOptions().setExact(true)).count() > 0
            }

            UserListHelper.searchUser(page, "${target.firstName} ${target.discord}")

            waitFor(
                onTimeoutMessage = { "Expected filtered paid user ${target.username} to remain visible" }
            ) {
                page.getByText(target.username, Page.GetByTextOptions().setExact(true)).count() > 0
            }
            assertPw(page.getByText(other.username, Page.GetByTextOptions().setExact(true))).hasCount(0)
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
