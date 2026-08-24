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

    /** Send one named recovery email; a row offers one button per email it can send. */
    fun clickSend(page: Page, purpose: String, userId: Long) {
        TestIdLocatorHelper.byTestId(page, "recovery-user-send-btn-$purpose-$userId").click()
    }

    /** Read one named recovery email; paired with the send button for the same email. */
    fun clickPreview(page: Page, purpose: String, userId: Long) {
        TestIdLocatorHelper.byTestId(page, "recovery-user-preview-btn-$purpose-$userId").click()
    }

    fun rowCount(page: Page, panelKey: String, userId: Long): Int {
        return TestIdLocatorHelper.byTestId(page, "recovery-user-list-$panelKey")
            .locator("[data-testid='recovery-user-row-$userId']")
            .count()
    }
}
