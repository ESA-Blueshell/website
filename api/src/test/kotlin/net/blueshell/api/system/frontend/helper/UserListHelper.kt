package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole

object UserListHelper {
    fun searchUser(page: Page, query: String) {
        page.getByRole(
            AriaRole.TEXTBOX,
            Page.GetByRoleOptions().setName("Search for a user").setExact(false)
        ).first().fill(query)
    }
}
