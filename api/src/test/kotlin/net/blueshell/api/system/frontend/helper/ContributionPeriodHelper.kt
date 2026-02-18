package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.function.Predicate

object ContributionPeriodHelper {
    private val FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun label(startDate: LocalDate, endDate: LocalDate): String {
        return "${startDate.format(FORMATTER)} - ${endDate.format(FORMATTER)}"
    }

    fun createPeriod(page: Page, startDate: LocalDate, endDate: LocalDate): Int {
        val response = page.waitForResponse(
            Predicate { resp ->
                resp.request().method() == "POST" &&
                    resp.url().contains("/contributionPeriods")
            }
        ) {
            page.locator("button:has(i.mdi-plus)").first().click()
            page.getByLabel("Start Date").fill(startDate.toString())
            page.getByLabel("End Date").fill(endDate.toString())
            page.getByLabel("Half Year Fee").fill("25")
            page.getByLabel("Full Year Fee").fill("45")
            page.getByLabel("Alumni Fee").fill("10")
            page.getByRole(
                AriaRole.BUTTON,
                Page.GetByRoleOptions().setName("Create").setExact(true)
            ).click()
        }
        return response.status()
    }

    fun selectPeriod(page: Page, periodLabel: String) {
        page.getByText(periodLabel, Page.GetByTextOptions().setExact(false)).first().click()
    }
}
