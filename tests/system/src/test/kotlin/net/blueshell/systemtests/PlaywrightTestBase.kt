package net.blueshell.systemtests

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

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
        awaitStackWarm(browser)
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

    companion object {
        /**
         * Default per-action and per-navigation timeout, shared with
         * [POLL_TIMEOUT_MS]. Five seconds is a ceiling: an action that needs
         * longer is waiting on the wrong signal — a locator resolved before
         * the page held the data, or an assertion racing a request the test
         * never awaited. Fix the signal rather than the number.
         */
        const val DEFAULT_TIMEOUT_MS: Double = POLL_TIMEOUT_MS.toDouble()

        /**
         * What the stack gets to answer its first request, once per JVM.
         *
         * Not a test budget, and not the ceiling above being raised: nothing is asserted here.
         * `docker compose up --wait` calls a container healthy as soon as its own check passes,
         * which is before nginx has served a page or the api has answered anything. The first
         * navigation of a suite therefore pays for a cold nginx, a cold JVM and a first render
         * all at once, and whichever test happened to run first was charged the lot: a five
         * second ceiling every other test in the shard met comfortably.
         *
         * So the cold start is waited for here, before any test runs, and the ceiling stays
         * where it is. A test that then needs longer than five seconds is still waiting on the
         * wrong signal, which is what that number is there to say.
         */
        private const val COLD_START_BUDGET_MS: Long = 90_000

        /** Once per JVM, which with one JVM per shard is once per shard. */
        private val warmed = AtomicBoolean(false)

        /**
         * HTTP/1.1 explicitly.
         *
         * The client defaults to HTTP/2, which against a cleartext origin means asking for an
         * h2c upgrade on the first request. The frontend's server does not answer that and the
         * request hangs until its own timeout: measured here, probes of an origin serving 200
         * to curl in two milliseconds timed out from Java for ninety seconds together, and the
         * gate would have failed the suite it was written to steady.
         */
        private val http: HttpClient by lazy {
            HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build()
        }

        /**
         * Waits for the stack to be answering, and pays the first page load itself.
         *
         * Two steps, because they are two different costs. The api is asked for its health
         * until it answers, which is the boot; then one throwaway page loads the frontend,
         * which is nginx reading its files, the browser parsing the bundle and the fonts
         * arriving. Whatever that costs, it is spent here rather than inside a test.
         */
        private fun awaitStackWarm(browser: Browser) {
            if (!warmed.compareAndSet(false, true)) return

            pollFor("the api to report itself healthy", COLD_START_BUDGET_MS) {
                answers("${TestEnvironment.apiUrl}/health")
            }
            pollFor("the frontend to serve a page", COLD_START_BUDGET_MS) {
                answers(TestEnvironment.frontendUrl)
            }

            val context = browser.newContext(Browser.NewContextOptions().setIgnoreHTTPSErrors(true))
            try {
                val page = context.newPage()
                page.setDefaultNavigationTimeout(COLD_START_BUDGET_MS.toDouble())
                page.navigate(TestEnvironment.frontendUrl)
            } finally {
                context.close()
            }
        }

        /** Whether an address answers with something other than a failure. */
        private fun answers(url: String): Boolean = runCatching {
            // A URI with no path at all is not a request Java will make, and `frontendUrl` is
            // an origin: `http://host:3000` needs the slash curl adds for you.
            val uri = URI.create(url).let { if (it.path.isNullOrEmpty()) URI.create("$url/") else it }
            val request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build()
            http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() < 400
        }.getOrDefault(false)
    }
}
