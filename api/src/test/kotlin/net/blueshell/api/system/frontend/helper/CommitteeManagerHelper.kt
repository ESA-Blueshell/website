package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Page

object CommitteeManagerHelper {
    fun open(page: Page, frontendUrl: String) {
        page.navigate("$frontendUrl/committees/manage")
        page.waitForURL("**/committees/manage**")
    }

    fun openCreateForm(page: Page) {
        page.getByText("Create new committee", Page.GetByTextOptions().setExact(false)).first().click()
    }
}
