package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Page

object MemberManagerHelper {
    fun open(page: Page, frontendUrl: String) {
        page.navigate("$frontendUrl/members/manage")
        page.waitForURL("**/members/manage**")
    }

    fun openNonMembers(page: Page) {
        page.locator("[data-testid='member-user-list-toggle-non-members']").first().click()
    }

    fun openMembers(page: Page) {
        page.locator("[data-testid='member-user-list-toggle-members']").first().click()
    }

    fun searchNonMembers(page: Page, query: String) {
        UserListHelper.searchUser(page, query, searchTestId = "member-user-list-search-non-members")
    }

    fun searchMembers(page: Page, query: String) {
        UserListHelper.searchUser(page, query, searchTestId = "member-user-list-search-members")
    }

    fun clickAddUser(page: Page) {
        page.locator("[data-testid='member-user-list-add-user-btn-non-members']").first().click()
    }

    fun clickUserRow(page: Page, userId: Long) {
        page.locator("[data-testid='member-user-toggle-$userId']").first().click()
    }

    fun clickStartMembership(page: Page, userId: Long) {
        page.locator("[data-testid='member-user-start-membership-btn-$userId']").first().click()
    }

    fun clickEndMembership(page: Page, userId: Long) {
        page.locator("[data-testid='member-user-end-membership-btn-$userId']").first().click()
    }
}
