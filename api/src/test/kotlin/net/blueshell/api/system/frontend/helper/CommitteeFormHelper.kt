package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole

object CommitteeFormHelper {
    fun fillCommittee(page: Page, name: String, description: String) {
        page.getByLabel("Committee name").fill(name)
        page.getByLabel("Description").fill(description)
    }

    fun addMember(page: Page, role: String, fullName: String, index: Int = 0) {
        val hasRoleRows = page.getByLabel("Role").count() > 0
        if (index > 0 || !hasRoleRows) {
            page.locator("[data-testid='committee-form-add-member-btn']").first().click()
        }
        page.getByLabel("Role").nth(index).fill(role)
        page.getByRole(
            AriaRole.COMBOBOX,
            Page.GetByRoleOptions().setName("Member name").setExact(false)
        ).nth(index).fill(fullName)
        page.getByRole(
            AriaRole.COMBOBOX,
            Page.GetByRoleOptions().setName("Member name").setExact(false)
        ).nth(index).press("Enter")
    }

    fun removeFirstMember(page: Page) {
        page.locator("[data-testid^='committee-form-remove-member-btn-']").first().click()
    }

    fun submit(page: Page) {
        page.locator("[data-testid='committee-form-submit-btn']").first().click()
    }
}
