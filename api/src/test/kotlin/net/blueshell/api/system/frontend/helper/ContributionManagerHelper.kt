package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Page

object ContributionManagerHelper {
    fun open(page: Page, frontendUrl: String) {
        page.navigate("$frontendUrl/contributions/manage")
        page.waitForURL("**/contributions/manage**")
    }

    fun selectPeriodById(page: Page, periodId: Long) {
        page.locator("[data-testid='contribution-period-select-btn-$periodId']").first().click()
    }

    fun selectPeriod(page: Page, periodLabel: String) {
        page.getByText(periodLabel, Page.GetByTextOptions().setExact(false)).first().click()
    }

    fun openSection(page: Page, panelKey: String) {
        page.locator("[data-testid='contribution-user-list-toggle-$panelKey']").first().click()
    }

    fun searchUser(page: Page, panelKey: String, query: String) {
        UserListHelper.searchUser(page, query, searchTestId = "contribution-user-list-search-$panelKey")
    }

    fun togglePaidButton(page: Page, userId: Long) {
        page.locator("[data-testid='contribution-user-toggle-paid-btn-$userId']").first().click()
    }
}
