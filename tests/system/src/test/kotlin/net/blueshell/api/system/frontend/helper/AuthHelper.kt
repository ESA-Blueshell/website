package net.blueshell.api.system.frontend.helper

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.RequestOptions
import net.blueshell.systemtests.TestEnvironment
import net.blueshell.systemtests.awaitResponseFrom

object AuthHelper {
    fun submitLogin(
        page: Page,
        frontendUrl: String,
        username: String,
        password: String,
    ): Int {
        // Wipe any session left over from an earlier login inside the
        // same browser context. Without this, the SPA hits `/login`,
        // notices the still-valid auth cookie, and redirects away
        // before Playwright can fill the form — a race that hid under
        // the in-process Spring Boot stack and surfaces against the
        // slower compose api.
        page.context().clearCookies()
        page.navigate("$frontendUrl/login/")
        readyForSignIn(page, username)
        LoginDomainHelper.fillLoginCredentials(page, username, password)

        val response =
            page.awaitResponseFrom(
                control = LoginDomainHelper.loginSubmitButton(page),
                expected = "POST /auth",
            ) { it.url().contains("/auth") && it.request().method() == "POST" }

        if (response.status() == 200) {
            stepUp(page)
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

    /** Counts the browser's sign-in as proved just now, as a code would. */
    fun stepUp(page: Page) {
        val userAgent = page.evaluate("() => navigator.userAgent") as String
        page.request().post(
            "${TestEnvironment.apiUrl}/test-support/step-up",
            RequestOptions.create().setHeader("User-Agent", userAgent),
        )
    }

    /**
     * Answers the two-factor offer and, for somebody with two-factor, puts a trusted-browser cookie
     * for this very browser into the context, so the form signs in with the password alone.
     */
    fun readyForSignIn(
        page: Page,
        username: String,
    ) {
        val userAgent = page.evaluate("() => navigator.userAgent") as String
        page.request().post(
            "${TestEnvironment.apiUrl}/test-support/sign-in-ready?username=$username",
            RequestOptions.create().setHeader("User-Agent", userAgent),
        )
    }
}
