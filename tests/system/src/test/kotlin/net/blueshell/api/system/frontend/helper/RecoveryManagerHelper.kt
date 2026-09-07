package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Page

object RecoveryManagerHelper {
    fun open(page: Page, frontendUrl: String) {
        page.navigate("$frontendUrl/recovery/manage")
        page.waitForURL("**/recovery/manage**")
    }

    /**
     * Opens the pane and waits for it to stop growing.
     *
     * `v-expand-transition` shows the pane at about two pixels and grows it for some 280ms, and a
     * row inside it holds one bounding box long enough for Playwright to call it stable. A click
     * aimed there is delivered to whatever has arrived at those coordinates once the pane moves
     * on: the action is never entered, nothing is refused, and no request is made. #1212 proved
     * that shape on the committee panel; this is the same transition around a restore button.
     */
    fun openSection(page: Page, panelKey: String) {
        val toggle = TestIdLocatorHelper.byTestId(page, "recovery-user-list-toggle-$panelKey")
        if (toggle.getAttribute("aria-expanded") != "true") {
            toggle.click()
        }
        ExpandPanelHelper.waitForOpened(page, "recovery-user-list-panel-$panelKey")
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
