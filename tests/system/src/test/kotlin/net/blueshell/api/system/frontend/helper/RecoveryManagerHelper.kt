package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

object RecoveryManagerHelper {
    fun open(
        page: Page,
        frontendUrl: String,
    ) {
        page.navigate("$frontendUrl/management/recovery")
        page.waitForURL("**/management/recovery**")
    }

    /** Narrows the one list to the accounts the old pane held: `inactive`, `active` or `deleted`. */
    fun openSection(
        page: Page,
        panelKey: String,
    ) {
        val state = if (panelKey == "inactive") "not-activated" else panelKey
        TestIdLocatorHelper.byTestId(page, "recovery-filter-state-search").click()
        TestIdLocatorHelper.byTestId(page, "recovery-filter-state-$state").click()
    }

    @Suppress("UNUSED_PARAMETER")
    fun searchUser(
        page: Page,
        panelKey: String,
        query: String,
    ) {
        TestIdLocatorHelper.byTestId(page, "recovery-search").fill(query)
    }

    fun clickAction(
        page: Page,
        actionType: String,
        userId: Long,
    ) {
        TestIdLocatorHelper.byTestId(page, "recovery-user-action-btn-$actionType-$userId").click()
    }

    /**
     * Open the one recovery email this row sends. Reading it is how it is sent: the row
     * button renders the email, and the dialog carries the send.
     */
    fun emailButton(
        page: Page,
        purpose: String,
        userId: Long,
    ): Locator = TestIdLocatorHelper.byTestId(page, "recovery-user-send-btn-$purpose-$userId")

    fun openEmail(
        page: Page,
        purpose: String,
        userId: Long,
    ) {
        emailButton(page, purpose, userId).click()
    }

    fun sendButton(page: Page): Locator = TestIdLocatorHelper.byTestId(page, "email-preview-send-btn")

    /** Send the email currently open, from the dialog that is showing it. */
    fun confirmSend(page: Page) {
        val send = sendButton(page)
        send.waitFor()
        send.click()
    }

    /** Whether the row offers to send this email at all. */
    fun offersEmail(
        page: Page,
        purpose: String,
        userId: Long,
    ): Boolean = emailButton(page, purpose, userId).count() > 0

    /** How many of the account's rows stand in the state the old pane held; a deleted account has two. */
    fun rowCount(
        page: Page,
        panelKey: String,
        userId: Long,
    ): Int {
        val word =
            when (panelKey) {
                "inactive" -> "Not activated"
                "active" -> "Active"
                else -> "Deleted"
            }
        return page
            .locator("[data-testid='recovery-state-$userId']")
            .allTextContents()
            .count { it.trim().startsWith(word) }
    }
}
