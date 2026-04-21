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
            TestIdLocatorHelper.byTestId(page, "contribution-period-add-btn").click()
            TestIdLocatorHelper.textInput(page, "contribution-period-start-date-field").fill(startDate.toString())
            TestIdLocatorHelper.textInput(page, "contribution-period-end-date-field").fill(endDate.toString())
            TestIdLocatorHelper.textInput(page, "contribution-period-half-year-fee-field").fill("25")
            TestIdLocatorHelper.textInput(page, "contribution-period-full-year-fee-field").fill("45")
            TestIdLocatorHelper.textInput(page, "contribution-period-alumni-fee-field").fill("10")
            TestIdLocatorHelper.byTestId(page, "contribution-period-submit-btn").click()
        }
        return response.status()
    }

    fun selectPeriod(page: Page, periodLabel: String) {
        page.getByText(periodLabel, Page.GetByTextOptions().setExact(false)).first().click()
    }
}
