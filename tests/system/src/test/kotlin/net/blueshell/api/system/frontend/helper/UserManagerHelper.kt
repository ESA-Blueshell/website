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

    /** Deleting lives on a person's own page: opens it from the list and answers its Delete button. */
    fun deleteButton(
        page: Page,
        userId: Long,
    ): Locator {
        val button = TestIdLocatorHelper.byTestId(page, "user-delete")
        if (!button.isVisible) {
            TestIdLocatorHelper.byTestId(page, "member-manager-open-$userId").click()
            page.waitForURL("**/management/users/$userId")
        }
        return button
    }

    fun clickDeleteUser(
        page: Page,
        userId: Long,
    ) {
        deleteButton(page, userId).click()
    }

    /** Confirms, then waits for the list: the person's page only goes back there once the account is gone. */
    fun confirmDelete(page: Page) {
        TestIdLocatorHelper.byTestId(page, "confirm-go").click()
        page.waitForURL("**/management/users")
    }
}
