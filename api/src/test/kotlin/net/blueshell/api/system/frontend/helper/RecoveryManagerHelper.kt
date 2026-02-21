package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Page

object RecoveryManagerHelper {
    fun open(page: Page, frontendUrl: String) {
        page.navigate("$frontendUrl/recovery/manage")
        page.waitForURL("**/recovery/manage**")
    }

    fun openSection(page: Page, panelKey: String) {
        TestIdLocatorHelper.byTestId(page, "recovery-user-list-toggle-$panelKey").click()
    }

    fun searchUser(page: Page, panelKey: String, query: String) {
        UserListHelper.searchUser(page, query, searchTestId = "recovery-user-list-search-$panelKey")
    }

    fun clickAction(page: Page, actionType: String, userId: Long) {
        TestIdLocatorHelper.byTestId(page, "recovery-user-action-btn-$actionType-$userId").click()
    }
}
