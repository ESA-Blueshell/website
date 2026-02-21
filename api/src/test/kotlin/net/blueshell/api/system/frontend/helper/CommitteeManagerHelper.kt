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
        page.locator("[data-testid='committee-row-$committeeId']").first()

    fun openCreateForm(page: Page) {
        page.locator("[data-testid='$CREATE_TOGGLE_BUTTON_TEST_ID']").first().click()
        page.locator("[data-testid='$CREATE_FORM_TEST_ID']").first().waitFor()
    }

    fun openEditForm(page: Page, committeeId: Long) {
        page.locator("[data-testid='committee-edit-btn-$committeeId']").first().click()
        page.locator("[data-testid='committee-manager-edit-form-$committeeId']").first().waitFor()
    }

    fun openDeleteDialog(page: Page, committeeId: Long) {
        page.locator("[data-testid='committee-delete-btn-$committeeId']").first().click()
    }
}
