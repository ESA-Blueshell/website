package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Page

object AuthHelper {
    fun submitLogin(page: Page, frontendUrl: String, username: String, password: String): Int {
        page.navigate("$frontendUrl/login/")
        LoginDomainHelper.fillLoginCredentials(page, username, password)

        val response = page.waitForResponse({ response ->
            response.url().contains("/auth") && response.request().method() == "POST"
        }) {
            LoginDomainHelper.clickLoginSubmit(page)
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
