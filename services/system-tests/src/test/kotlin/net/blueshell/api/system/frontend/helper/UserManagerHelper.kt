package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Page

object UserManagerHelper {
    fun open(page: Page, frontendUrl: String) {
        page.navigate("$frontendUrl/user-manager")
        page.waitForURL("**/user-manager**")
    }

    fun search(page: Page, query: String) {
        UserListHelper.searchUser(page, query, searchTestId = "member-manager-search-input")
    }

    fun clickAddUser(page: Page) {
        TestIdLocatorHelper.byTestId(page, "bulk-actions-menu-btn").click()
        TestIdLocatorHelper.byTestId(page, "member-manager-add-user-btn").click()
    }

    fun clickDeleteUser(page: Page, userId: Long) {
        TestIdLocatorHelper.byTestId(page, "member-manager-delete-btn-$userId").click()
    }

    fun confirmDelete(page: Page) {
        TestIdLocatorHelper.byTestId(page, "deletion-confirmation-confirm-btn").click()
    }
}
