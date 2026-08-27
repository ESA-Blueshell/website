package net.blueshell.systemtests

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.PlaywrightException
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Spring-free base class for browser-driven system tests. Mirrors the
 * shape of personal-stack-2's PlaywrightTestBase: one Browser per class,
 * one BrowserContext per test, headless Chromium, default 5s timeouts.
 *
 * The shared lifecycle lives here so concrete tests can layer on
 * domain-specific helpers without re-implementing setup.
 */
@ExtendWith(PlaywrightShardCondition::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class PlaywrightTestBase {
    protected val apiUrl: String = TestEnvironment.apiUrl
    protected val frontendUrl: String = TestEnvironment.frontendUrl

    private lateinit var playwright: Playwright
    private lateinit var browser: Browser
    protected lateinit var context: BrowserContext
    protected lateinit var page: Page

    @BeforeAll
    fun launchBrowser() {
        playwright = Playwright.create()
        browser = playwright.chromium().launch(
            BrowserType.LaunchOptions().setHeadless(true),
        )
    }

    @AfterAll
    fun closeBrowser() {
        if (::browser.isInitialized) browser.close()
        if (::playwright.isInitialized) playwright.close()
    }

    @BeforeEach
    fun createContext() {
        context = browser.newContext(
            Browser.NewContextOptions()
                .setIgnoreHTTPSErrors(true)
                // Playwright's 1280 default sits exactly on Vuetify's lgAndUp
                // breakpoint, so a scrollbar tips layouts to their mobile variant.
                .setViewportSize(1600, 900),
        )
        context.setDefaultTimeout(DEFAULT_TIMEOUT_MS)
        context.setDefaultNavigationTimeout(DEFAULT_TIMEOUT_MS)
        page = context.newPage()
        page.setDefaultTimeout(DEFAULT_TIMEOUT_MS)
        page.setDefaultNavigationTimeout(DEFAULT_TIMEOUT_MS)
        HttpFailureLog.clear()
        page.onRequest { request ->
            if (!request.url().contains("/assets/") && !request.url().endsWith(".js")) {
                HttpFailureLog.recordRequest(request.method(), request.url())
            }
        }
        page.onResponse { response ->
            if (response.status() >= 400) {
                HttpFailureLog.record(response.status(), response.request().method(), response.url())
            }
        }
    }

    @AfterEach
    fun closeContext() {
        if (::context.isInitialized) context.close()
    }

    /**
     * Retries `page.navigate(url)` a few times on ECONNREFUSED — useful
     * during the brief window after `docker compose up --wait` reports
     * healthy while traefik / nginx is still binding ports.
     */
    protected fun navigateWithRetry(url: String, attempts: Int = 3) {
        var lastException: PlaywrightException? = null
        repeat(attempts) { attempt ->
            try {
                page.navigate(url)
                return
            } catch (e: PlaywrightException) {
                if (e.message?.contains("ERR_CONNECTION_REFUSED") == true) {
                    lastException = e
                    if (attempt < attempts - 1) {
                        Thread.sleep(2_000L * (attempt + 1))
                    }
                } else {
                    throw e
                }
            }
        }
        throw lastException!!
    }

    companion object {
        /**
         * Default per-action and per-navigation timeout, shared with
         * [POLL_TIMEOUT_MS]. Five seconds is a ceiling: an action that needs
         * longer is waiting on the wrong signal — a locator resolved before
         * the page held the data, or an assertion racing a request the test
         * never awaited. Fix the signal rather than the number.
         */
        const val DEFAULT_TIMEOUT_MS: Double = POLL_TIMEOUT_MS.toDouble()
    }
}
