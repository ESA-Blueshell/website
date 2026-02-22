package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole

object UserListHelper {
    fun searchUser(page: Page, query: String, index: Int = 0, searchTestId: String? = null) {
        if (searchTestId != null) {
            TestIdLocatorHelper.textInput(page, searchTestId).fill(query)
            return
        }

        page.getByRole(
            AriaRole.TEXTBOX,
            Page.GetByRoleOptions().setName("Search for a user").setExact(false)
        ).nth(index).fill(query)
    }
}
