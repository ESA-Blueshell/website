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
            page.getByRole(
                AriaRole.BUTTON,
                Page.GetByRoleOptions().setName("Add member").setExact(false)
            ).first().click()
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
        page.locator(".my-3 button:has(i.mdi-close)").first().click()
    }

    fun submit(page: Page) {
        page.getByRole(
            AriaRole.BUTTON,
            Page.GetByRoleOptions().setName("Submit").setExact(false)
        ).click()
    }
}
