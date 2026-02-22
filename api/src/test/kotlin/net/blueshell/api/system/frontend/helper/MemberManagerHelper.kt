package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Page

object MemberManagerHelper {
    fun open(page: Page, frontendUrl: String) {
        page.navigate("$frontendUrl/members/manage")
        page.waitForURL("**/members/manage**")
    }

    fun openNonMembers(page: Page) {
        TestIdLocatorHelper.byTestId(page, "member-user-list-toggle-non-members").click()
    }

    fun openMembers(page: Page) {
        TestIdLocatorHelper.byTestId(page, "member-user-list-toggle-members").click()
    }

    fun searchNonMembers(page: Page, query: String) {
        UserListHelper.searchUser(page, query, searchTestId = "member-user-list-search-non-members")
    }

    fun searchMembers(page: Page, query: String) {
        UserListHelper.searchUser(page, query, searchTestId = "member-user-list-search-members")
    }

    fun clickAddUser(page: Page) {
        TestIdLocatorHelper.byTestId(page, "member-user-list-add-user-btn-non-members").click()
    }

    fun clickUserRow(page: Page, userId: Long) {
        TestIdLocatorHelper.byTestId(page, "member-user-toggle-$userId").click()
    }

    fun clickStartMembership(page: Page, userId: Long) {
        TestIdLocatorHelper.byTestId(page, "member-user-start-membership-btn-$userId").click()
    }

    fun clickEndMembership(page: Page, userId: Long) {
        TestIdLocatorHelper.byTestId(page, "member-user-end-membership-btn-$userId").click()
    }
}
