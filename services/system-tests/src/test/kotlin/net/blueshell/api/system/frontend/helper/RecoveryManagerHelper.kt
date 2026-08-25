package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Page

object RecoveryManagerHelper {
    fun open(page: Page, frontendUrl: String) {
        page.navigate("$frontendUrl/recovery/manage")
        page.waitForURL("**/recovery/manage**")
    }

    fun openSection(page: Page, panelKey: String) {
        val toggle = TestIdLocatorHelper.byTestId(page, "recovery-user-list-toggle-$panelKey")
        if (toggle.getAttribute("aria-expanded") != "true") {
            toggle.click()
        }
    }

    fun searchUser(page: Page, panelKey: String, query: String) {
        UserListHelper.searchUser(page, query, searchTestId = "recovery-user-list-search-$panelKey")
    }

    fun clickAction(page: Page, actionType: String, userId: Long) {
        TestIdLocatorHelper.byTestId(page, "recovery-user-action-btn-$actionType-$userId").click()
    }

    /**
     * Open the one recovery email this row sends. Reading it is how it is sent: the row
     * button renders the email, and the dialog carries the send.
     */
    fun openEmail(page: Page, purpose: String, userId: Long) {
        TestIdLocatorHelper.byTestId(page, "recovery-user-send-btn-$purpose-$userId").click()
    }

    /** Send the email currently open, from the dialog that is showing it. */
    fun confirmSend(page: Page) {
        val send = TestIdLocatorHelper.byTestId(page, "email-preview-send-btn")
        send.waitFor()
        send.click()
    }

    /** Whether the row offers to send this email at all. */
    fun offersEmail(page: Page, purpose: String, userId: Long): Boolean =
        TestIdLocatorHelper.byTestId(page, "recovery-user-send-btn-$purpose-$userId").count() > 0

    fun rowCount(page: Page, panelKey: String, userId: Long): Int {
        return TestIdLocatorHelper.byTestId(page, "recovery-user-list-$panelKey")
            .locator("[data-testid='recovery-user-row-$userId']")
            .count()
    }
}
