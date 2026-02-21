package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Page

object ContributionManagerHelper {
    fun open(page: Page, frontendUrl: String) {
        page.navigate("$frontendUrl/contributions/manage")
        page.waitForURL("**/contributions/manage**")
    }

    fun selectPeriodById(page: Page, periodId: Long) {
        TestIdLocatorHelper.byTestId(page, "contribution-period-select-btn-$periodId").click()
    }

    fun selectPeriod(page: Page, periodLabel: String) {
        page.getByText(periodLabel, Page.GetByTextOptions().setExact(false)).first().click()
    }

    fun openSection(page: Page, panelKey: String) {
        TestIdLocatorHelper.byTestId(page, "contribution-user-list-toggle-$panelKey").click()
    }

    fun searchUser(page: Page, panelKey: String, query: String) {
        UserListHelper.searchUser(page, query, searchTestId = "contribution-user-list-search-$panelKey")
    }

    fun togglePaidButton(page: Page, userId: Long) {
        TestIdLocatorHelper.byTestId(page, "contribution-user-toggle-paid-btn-$userId").click()
    }
}
