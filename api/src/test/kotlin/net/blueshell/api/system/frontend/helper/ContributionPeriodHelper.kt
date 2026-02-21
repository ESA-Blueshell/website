package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Page
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
            page.locator("[data-testid='contribution-period-add-btn']").first().click()
            page.locator("[data-testid='contribution-period-start-date-field']").first().locator("input").first()
                .fill(startDate.toString())
            page.locator("[data-testid='contribution-period-end-date-field']").first().locator("input").first()
                .fill(endDate.toString())
            page.locator("[data-testid='contribution-period-half-year-fee-field']").first().locator("input").first()
                .fill("25")
            page.locator("[data-testid='contribution-period-full-year-fee-field']").first().locator("input").first()
                .fill("45")
            page.locator("[data-testid='contribution-period-alumni-fee-field']").first().locator("input").first()
                .fill("10")
            page.locator("[data-testid='contribution-period-submit-btn']").first().click()
        }
        return response.status()
    }

    fun selectPeriod(page: Page, periodLabel: String) {
        page.getByText(periodLabel, Page.GetByTextOptions().setExact(false)).first().click()
    }
}
