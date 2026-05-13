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
        val combobox = page.getByRole(
            AriaRole.COMBOBOX,
            Page.GetByRoleOptions().setName("Member name").setExact(false),
        ).nth(index)
        combobox.fill(fullName)
        // Click the matching dropdown option rather than pressing Enter:
        // pressing Enter relies on Vuetify's auto-select-first having
        // populated against the `users` prop, but the parent page
        // loads users asynchronously after mount — on a fresh create
        // form the autocomplete can be empty when this helper runs.
        // Waiting for the option locks the binding without polling.
        page.getByRole(
            AriaRole.OPTION,
            Page.GetByRoleOptions().setName(fullName).setExact(false),
        ).first().click()
    }

    fun removeFirstMember(page: Page) {
        TestIdLocatorHelper.byTestIdPrefix(page, "committee-form-remove-member-btn-").click()
    }

    fun submit(page: Page) {
        TestIdLocatorHelper.byTestId(page, "committee-form-submit-btn").click()
    }
}
