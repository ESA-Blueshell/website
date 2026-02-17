package net.blueshell.api.system

import com.microsoft.playwright.Browser
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import net.blueshell.api.ApiApplication
import net.blueshell.api.config.TruncateTestDatabaseListener
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.domain.user.persistence.repository.UserRepository
import net.blueshell.api.shared.enums.Role
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.opentest4j.TestAbortedException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestExecutionListeners
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat as assertPw

/**
 * End-to-end browser tests for user account creation and sign-in flows.
 *
 * Prerequisites:
 * - frontend reachable at SYSTEM_FRONTEND_URL (default: http://frontend:3000)
 * - this SpringBootTest instance serves API on port 8080
 */
@Tag("system")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
@TestExecutionListeners(
    listeners = [TruncateTestDatabaseListener::class],
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS
)
@SpringBootTest(
    classes = [ApiApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties = ["server.port=8080"]
)
class UserAuthSystemTest {

    private val frontendBaseUrl = System.getProperty("system.frontend.url")
        ?: System.getenv("SYSTEM_FRONTEND_URL")
        ?: "http://frontend:3000"
    private val apiBaseUrl = System.getProperty("system.api.url")
        ?: System.getenv("SYSTEM_API_URL")
        ?: "http://localhost:8080"

    @Autowired
    private lateinit var userRepository: UserRepository

    private var playwright: Playwright? = null
    private var browser: Browser? = null

    @BeforeAll
    fun setUp() {
        assumeTrue(waitUntilReachable("$frontendBaseUrl/login"), "Frontend is not reachable at $frontendBaseUrl")
        assumeTrue(waitUntilReachable("$apiBaseUrl/health"), "API is not reachable at $apiBaseUrl")

        try {
            playwright = Playwright.create()
            browser = playwright!!.chromium().launch(
                com.microsoft.playwright.BrowserType.LaunchOptions()
                    .setHeadless(true)
            )
        } catch (e: Exception) {
            throw TestAbortedException("Playwright browser setup failed: ${e.message}", e)
        }
    }

    @AfterAll
    fun tearDown() {
        browser?.close()
        playwright?.close()
    }

    @Test
    fun `create account flow shows success message`() {
        val context = browser!!.newContext()
        val page = context.newPage()
        context.use {
            val credentials = createAccountThroughUi(page)
            val persisted = waitForUserByUsername(credentials.username)

            assertPw(page.getByText("Your account has successfully been created!"))
                .isVisible()
            assertThat(persisted.email).isEqualTo(credentials.email)
            assertThat(persisted.enabled).isFalse()
            assertThat(persisted.roles).contains(Role.GUEST)
            assertThat(persisted.password).isNotEqualTo(credentials.password)
        }
    }

    @Test
    fun `login flow returns error for unactivated account`() {
        val context = browser!!.newContext()
        val page = context.newPage()
        context.use {
            val credentials = createAccountThroughUi(page)

            page.navigate("$frontendBaseUrl/login")
            page.getByLabel("Username").fill(credentials.username)
            page.getByLabel("Password", Page.GetByLabelOptions().setExact(false)).fill(credentials.password)
            page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON, Page.GetByRoleOptions().setName("Login"))
                .click()

            assertPw(page.getByText("Incorrect login credentials. Please double check your username and password."))
                .isVisible()

            val persisted = waitForUserByUsername(credentials.username)
            assertThat(persisted.enabled).isFalse()
        }
    }

    private fun createAccountThroughUi(page: Page): Credentials {
        val suffix = System.currentTimeMillis().toString().takeLast(8)
        val username = "sysuser$suffix"
        val email = "sysuser$suffix@example.com"
        val password = "Passw0rd!$suffix"

        page.navigate("$frontendBaseUrl/account/create")

        page.getByLabel("Initials", Page.GetByLabelOptions().setExact(false)).fill("SU")
        page.getByLabel("First Name", Page.GetByLabelOptions().setExact(false)).fill("System")
        page.getByLabel("Surname", Page.GetByLabelOptions().setExact(false)).fill("User$suffix")
        page.getByLabel("Username", Page.GetByLabelOptions().setExact(false)).fill(username)
        page.getByLabel("Discord", Page.GetByLabelOptions().setExact(false)).fill("sysuser$suffix")
        page.getByLabel("E-mail", Page.GetByLabelOptions().setExact(false)).fill(email)
        page.getByLabel("Phone Number", Page.GetByLabelOptions().setExact(false)).fill("+3161234$suffix")
        page.getByLabel("Password*", Page.GetByLabelOptions().setExact(false)).fill(password)
        page.getByLabel("Password (repeated)", Page.GetByLabelOptions().setExact(false)).fill(password)

        page.getByRole(
            com.microsoft.playwright.options.AriaRole.BUTTON,
            Page.GetByRoleOptions().setName("Create Account")
        )
            .click()

        assertPw(page.getByText("Your account has successfully been created!"))
            .isVisible()

        return Credentials(username = username, email = email, password = password)
    }

    private fun waitForUserByUsername(username: String, retries: Int = 20, waitMillis: Long = 200): User {
        repeat(retries) { attempt ->
            val found = userRepository.findByUsername(username)
            if (found.isPresent) {
                return found.get()
            }
            if (attempt < retries - 1) {
                Thread.sleep(waitMillis)
            }
        }
        throw AssertionError("Expected user '$username' to be persisted")
    }

    private fun waitUntilReachable(url: String, retries: Int = 20, waitMillis: Long = 1_000): Boolean {
        repeat(retries) { attempt ->
            if (isReachableOnce(url)) {
                return true
            }

            if (attempt < retries - 1) {
                Thread.sleep(waitMillis)
            }
        }
        return false
    }

    private fun isReachableOnce(url: String): Boolean {
        return try {
            val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
            val req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build()
            val response = client.send(req, HttpResponse.BodyHandlers.discarding())
            response.statusCode() in 200..399
        } catch (_: Exception) {
            false
        }
    }

    private data class Credentials(
        val username: String,
        val email: String,
        val password: String,
    )
}
