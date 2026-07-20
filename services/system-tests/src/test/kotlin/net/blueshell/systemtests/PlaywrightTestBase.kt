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
                // Pin a comfortable desktop viewport. The member manager only renders the
                // selectable desktop table (with the per-row bulk checkboxes) when Vuetify's
                // `lgAndUp` is true, which is the `>= 1280px` breakpoint. Playwright's default
                // 1280x720 sits exactly on that boundary, so once a long member list adds a
                // vertical scrollbar the layout width dips just below 1280, flips the manager
                // to the mobile list (which has no bulk checkboxes), and `selectUserRow` then
                // times out clicking a checkbox that is no longer laid out. A width safely above
                // the breakpoint (matching the e2e suite's 1440x900) keeps the desktop table and
                // its checkboxes rendered regardless of scrollbar width.
                .setViewportSize(1600, 900),
        )
        context.setDefaultTimeout(DEFAULT_TIMEOUT_MS)
        context.setDefaultNavigationTimeout(DEFAULT_TIMEOUT_MS)
        page = context.newPage()
        page.setDefaultTimeout(DEFAULT_TIMEOUT_MS)
        page.setDefaultNavigationTimeout(DEFAULT_TIMEOUT_MS)
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
         * Default per-action timeout. With the in-process Spring Boot
         * context running alongside the sharded matrix and
         * `app.jobs.auto-dispatch=true` flushing `SyncContactCommand`
         * / `EmailSenderService` between tests, a freshly-loaded
         * `/account` page that needs `/users/{id}` to resolve before
         * its form wrapper mounts can overshoot 15 s on a busy
         * shard. Matches Playwright's own default; locator polling
         * still returns immediately on fast pages.
         */
        const val DEFAULT_TIMEOUT_MS: Double = 30_000.0
    }
}
