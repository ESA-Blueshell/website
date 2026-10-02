package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

object UserManagerHelper {
    fun open(
        page: Page,
        frontendUrl: String,
    ) {
        page.navigate("$frontendUrl/management/users")
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

    /**
     * A row's actions live in its menu, so each one is reached by opening the menu first. The
     * trigger toggles, so an entry already showing is taken as it is rather than clicked shut.
     */
    private fun action(
        page: Page,
        userId: Long,
        testId: String,
    ): Locator {
        val entry = TestIdLocatorHelper.byTestId(page, "$testId-$userId")
        if (!entry.isVisible) TestIdLocatorHelper.byTestId(page, "member-manager-actions-$userId").click()
        return entry
    }

    /** Opens one of a person's tabs from their name on the list. */
    fun openTab(
        page: Page,
        userId: Long,
        tab: String,
    ) {
        TestIdLocatorHelper.byTestId(page, "member-manager-open-$userId").click()
        page.waitForURL("**/management/users/$userId")
        TestIdLocatorHelper.byTestId(page, "user-tab-$tab").click()
        page.waitForURL("**/management/users/$userId/$tab")
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
