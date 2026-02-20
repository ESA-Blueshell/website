package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole

object AuthHelper {
    fun submitLogin(page: Page, frontendUrl: String, username: String, password: String): Int {
        page.navigate("$frontendUrl/login/")
        page.getByLabel("Username").fill(username)
        page.getByRole(
            AriaRole.TEXTBOX,
            Page.GetByRoleOptions().setName("Password")
        ).fill(password)

        val response = page.waitForResponse({ response ->
            response.url().contains("/auth") && response.request().method() == "POST"
        }) {
            page.getByRole(
                AriaRole.BUTTON,
                Page.GetByRoleOptions().setName("Login")
            ).click()
        }

        if (response.status() == 200) {
            val deadline = System.currentTimeMillis() + 5_000
            while (System.currentTimeMillis() < deadline) {
                val hasLoginCookie = page.context().cookies().any { it.name == "login" }
                val leftLoginPage = !page.url().contains("/login")
                if (hasLoginCookie && leftLoginPage) break
                Thread.sleep(100)
            }
        }
        return response.status()
    }
}
