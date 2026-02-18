package net.blueshell.api.system.frontend.management

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import net.blueshell.api.domain.contribution.persistence.repository.ContributionRepository
import net.blueshell.api.factory.contribution.persistence.ContributionFactory
import net.blueshell.api.factory.user.persistence.UserFactory
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.system.frontend.FrontendSystemTestBase
import net.blueshell.api.system.frontend.helper.AuthHelper
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

        val uniqueOffset = (System.currentTimeMillis() % 10_000).toLong()
        val startDate = LocalDate.now().plusDays(1000L + uniqueOffset)
        val endDate = startDate.plusDays(30)
        val period = contributionFactory.createPeriod(startDate, endDate)
        val periodId = checkNotNull(period.id) { "Expected contribution period id" }
        val periodLabel = "${startDate.format(FORMATTER)} - ${endDate.format(FORMATTER)}"
        val memberId = checkNotNull(member.id) { "Expected member id" }

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, board.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            page.navigate("$frontendUrl/contributions/manage")
            page.waitForURL("**/contributions/manage**")

            waitFor(
                onTimeoutMessage = { "Expected contribution period '$periodLabel' to be visible" }
            ) {
                page.getByText(periodLabel, Page.GetByTextOptions().setExact(false)).count() > 0
            }
            page.getByText(periodLabel, Page.GetByTextOptions().setExact(false)).first().click()

            page.getByText("Contribution unpaid", Page.GetByTextOptions().setExact(true)).click()

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

            page.navigate("$frontendUrl/contributions/manage")
            page.waitForURL("**/contributions/manage**")

            waitFor(
                onTimeoutMessage = { "Expected contribution period '$periodLabel' to be visible for unmark flow" }
            ) {
                page.getByText(periodLabel, Page.GetByTextOptions().setExact(false)).count() > 0
            }
            page.getByText(periodLabel, Page.GetByTextOptions().setExact(false)).first().click()

            page.getByText("Contribution paid", Page.GetByTextOptions().setExact(true)).click()

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

    private companion object {
        val FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        const val DEFAULT_PASSWORD = "Password123!"
    }
}
