package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

object UserManagerHelper {
    fun open(
        page: Page,
        frontendUrl: String,
    ) {
        page.navigate("$frontendUrl/user-manager")
        page.waitForURL("**/management/users**")
    }

    fun search(
        page: Page,
        query: String,
    ) {
        TestIdLocatorHelper.byTestId(page, "member-manager-search-input").fill(query)
    }

    fun clickAddUser(page: Page) {
        TestIdLocatorHelper.byTestId(page, "member-manager-add-user-btn").click()
    }

    /** A row's actions live in its menu, so each one is reached by opening the menu first. */
    private fun action(
        page: Page,
        userId: Long,
        testId: String,
    ): Locator {
        TestIdLocatorHelper.byTestId(page, "member-manager-actions-$userId").click()
        return TestIdLocatorHelper.byTestId(page, "$testId-$userId")
    }

    fun clickEditRoles(
        page: Page,
        userId: Long,
    ) {
        action(page, userId, "member-manager-edit-roles-btn").click()
    }

    fun clickAccountSecurity(
        page: Page,
        userId: Long,
    ) {
        action(page, userId, "member-manager-account-security-btn").click()
    }

    /** Opens the row's menu and answers its Delete entry. */
    fun deleteButton(
        page: Page,
        userId: Long,
    ): Locator = action(page, userId, "member-manager-delete-btn")

    fun clickDeleteUser(
        page: Page,
        userId: Long,
    ) {
        deleteButton(page, userId).click()
    }

    fun confirmDelete(page: Page) {
        TestIdLocatorHelper.byTestId(page, "deletion-confirmation-confirm-btn").click()
    }
}
