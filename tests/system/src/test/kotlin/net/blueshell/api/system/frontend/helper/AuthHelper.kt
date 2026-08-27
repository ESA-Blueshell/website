package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Page

object AuthHelper {
    fun submitLogin(page: Page, frontendUrl: String, username: String, password: String): Int {
        // Wipe any session left over from an earlier login inside the
        // same browser context. Without this, the SPA hits `/login`,
        // notices the still-valid auth cookie, and redirects away
        // before Playwright can fill the form — a race that hid under
        // the in-process Spring Boot stack and surfaces against the
        // slower compose api.
        page.context().clearCookies()
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
