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
            TestIdLocatorHelper.byTestId(page, "committee-form-add-member-btn").click()
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
        TestIdLocatorHelper.byTestIdPrefix(page, "committee-form-remove-member-btn-").click()
    }

    fun submit(page: Page) {
        TestIdLocatorHelper.byTestId(page, "committee-form-submit-btn").click()
    }
}
