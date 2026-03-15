package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Page

object CommitteeManagerHelper {
    private const val CREATE_TOGGLE_BUTTON_TEST_ID = "committee-manager-create-toggle-btn"
    private const val CREATE_FORM_TEST_ID = "committee-manager-create-form"

    fun open(page: Page, frontendUrl: String) {
        page.navigate("$frontendUrl/committees/manage")
        page.waitForURL("**/committees/manage**")
    }

    fun committeeRow(page: Page, committeeId: Long) =
        TestIdLocatorHelper.byTestId(page, "committee-row-$committeeId")

    fun openCreateForm(page: Page) {
        TestIdLocatorHelper.byTestId(page, CREATE_TOGGLE_BUTTON_TEST_ID).click()
        TestIdLocatorHelper.byTestId(page, CREATE_FORM_TEST_ID).waitFor()
    }

    fun openEditForm(page: Page, committeeId: Long) {
        TestIdLocatorHelper.byTestId(page, "committee-edit-btn-$committeeId").click()
        TestIdLocatorHelper.byTestId(page, "committee-manager-edit-form-$committeeId").waitFor()
    }

    fun openDeleteDialog(page: Page, committeeId: Long) {
        TestIdLocatorHelper.byTestId(page, "committee-delete-btn-$committeeId").click()
    }
}
