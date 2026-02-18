package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Page

object RecoveryManagerHelper {
    fun open(page: Page, frontendUrl: String) {
        page.navigate("$frontendUrl/recovery/manage")
        page.waitForURL("**/recovery/manage**")
    }

    fun openSection(page: Page, title: String) {
        page.getByText(title, Page.GetByTextOptions().setExact(true)).click()
    }

    fun searchUser(page: Page, query: String) {
        UserListHelper.searchUser(page, query)
    }
}
