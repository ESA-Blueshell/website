package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Page

object ContributionManagerHelper {
    fun open(page: Page, frontendUrl: String) {
        page.navigate("$frontendUrl/contributions/manage")
        page.waitForURL("**/contributions/manage**")
    }

    fun selectPeriod(page: Page, periodLabel: String) {
        page.getByText(periodLabel, Page.GetByTextOptions().setExact(false)).first().click()
    }

    fun openSection(page: Page, title: String) {
        page.getByText(title, Page.GetByTextOptions().setExact(true)).click()
    }
}
