package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Page

object AddressManagerHelper {
    fun open(page: Page, frontendUrl: String) {
        page.navigate("$frontendUrl/addresses/manage")
        page.waitForURL("**/addresses/manage**")
    }

    fun openUsersWithAddress(page: Page) {
        TestIdLocatorHelper.byTestId(page, "address-user-list-toggle-with-address").click()
    }

    fun openUsersWithoutAddress(page: Page) {
        TestIdLocatorHelper.byTestId(page, "address-user-list-toggle-without-address").click()
    }

    fun searchUsersWithAddress(page: Page, query: String) {
        UserListHelper.searchUser(page, query, searchTestId = "address-user-list-search-with-address")
    }

    fun searchUsersWithoutAddress(page: Page, query: String) {
        UserListHelper.searchUser(page, query, searchTestId = "address-user-list-search-without-address")
    }

    fun clickEditAddress(page: Page, userId: Long) {
        TestIdLocatorHelper.byTestId(page, "address-user-edit-btn-$userId").click()
    }

    fun clickDeleteAddress(page: Page, userId: Long) {
        TestIdLocatorHelper.byTestId(page, "address-user-delete-btn-$userId").click()
    }
}
