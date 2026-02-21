package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Page

object AddressManagerHelper {
    fun open(page: Page, frontendUrl: String) {
        page.navigate("$frontendUrl/addresses/manage")
        page.waitForURL("**/addresses/manage**")
    }

    fun openUsersWithAddress(page: Page) {
        page.locator("[data-testid='address-user-list-toggle-with-address']").first().click()
    }

    fun openUsersWithoutAddress(page: Page) {
        page.locator("[data-testid='address-user-list-toggle-without-address']").first().click()
    }

    fun searchUsersWithAddress(page: Page, query: String) {
        UserListHelper.searchUser(page, query, searchTestId = "address-user-list-search-with-address")
    }

    fun searchUsersWithoutAddress(page: Page, query: String) {
        UserListHelper.searchUser(page, query, searchTestId = "address-user-list-search-without-address")
    }

    fun clickEditAddress(page: Page, userId: Long) {
        page.locator("[data-testid='address-user-edit-btn-$userId']").first().click()
    }

    fun clickDeleteAddress(page: Page, userId: Long) {
        page.locator("[data-testid='address-user-delete-btn-$userId']").first().click()
    }
}
