package net.blueshell.api.system.frontend

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import net.blueshell.api.ApiApplication
import net.blueshell.api.config.TruncateTestDatabaseListener
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.domain.user.persistence.repository.UserRepository
import net.blueshell.api.platform.integration.job.repository.JobExecutionRepository
import net.blueshell.api.platform.integration.mock.MockJavaMailSender
import net.blueshell.api.shared.job.EmailJobs
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
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
import jakarta.mail.Multipart
import jakarta.mail.Part
import jakarta.mail.internet.MimeMessage

@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestExecutionListeners(
    listeners = [TruncateTestDatabaseListener::class],
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS
)
@SpringBootTest(
    classes = [ApiApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties = ["server.port=8080"]
)
abstract class FrontendSystemTestBase {

    protected val frontendBaseUrl = System.getProperty("system.frontend.url")
        ?: System.getenv("SYSTEM_FRONTEND_URL")
        ?: "http://frontend:3000"
    protected val apiBaseUrl = System.getProperty("system.api.url")
        ?: System.getenv("SYSTEM_API_URL")
        ?: "http://localhost:8080"

    @Autowired
    protected lateinit var userRepository: UserRepository

    @Autowired
    protected lateinit var mailSender: MockJavaMailSender

    @Autowired
    protected lateinit var jobExecutionRepository: JobExecutionRepository

    private var playwright: Playwright? = null
    private var browser: Browser? = null

    @BeforeAll
    fun setUpPlaywright() {
        assumeTrue(waitUntilReachable("$frontendBaseUrl/"), "Frontend is not reachable at $frontendBaseUrl")
        assumeTrue(waitUntilReachable("$apiBaseUrl/health"), "API is not reachable at $apiBaseUrl")

        try {
            playwright = Playwright.create()
            browser = playwright!!.chromium().launch(
                BrowserType.LaunchOptions().setHeadless(true)
            )
        } catch (e: Exception) {
            throw TestAbortedException("Playwright browser setup failed: ${e.message}", e)
        }
    }

    @BeforeEach
    fun clearOutbox() {
        mailSender.clear()
    }

    @AfterAll
    fun tearDownPlaywright() {
        browser?.close()
        playwright?.close()
    }

    protected fun withPage(block: (Page) -> Unit) {
        val context = checkNotNull(browser) { "Browser not initialized" }.newContext()
        val page = context.newPage()
        try {
            block(page)
        } finally {
            context.close()
        }
    }

    protected fun createAccountThroughUi(page: Page, url: String, submitButtonLabel: String, includeMemberProfile: Boolean): Credentials {
        val suffix = System.currentTimeMillis().toString().takeLast(8)
        val username = "sysuser$suffix"
        val email = "sysuser$suffix@example.com"
        val password = "Passw0rd!$suffix"

        page.navigate(url)
        page.getByLabel("Initials", Page.GetByLabelOptions().setExact(false)).fill("SU")
        page.getByLabel("First Name", Page.GetByLabelOptions().setExact(false)).fill("System")
        page.getByLabel("Surname", Page.GetByLabelOptions().setExact(false)).fill("User$suffix")
        page.getByLabel("Username", Page.GetByLabelOptions().setExact(false)).fill(username)
        page.getByLabel("Discord", Page.GetByLabelOptions().setExact(false)).fill("sysuser$suffix")
        page.getByLabel("E-mail", Page.GetByLabelOptions().setExact(false)).fill(email)
        page.getByLabel("Phone Number", Page.GetByLabelOptions().setExact(false)).fill("+3161234$suffix")
        page.getByLabel("Password*", Page.GetByLabelOptions().setExact(false)).fill(password)
        page.getByLabel("Password (repeated)", Page.GetByLabelOptions().setExact(false)).fill(password)

        if (includeMemberProfile) {
            page.getByLabel("Date of Birth", Page.GetByLabelOptions().setExact(false)).fill("1999-04-12")
            page.getByLabel("Gender", Page.GetByLabelOptions().setExact(false)).fill("X")
            page.getByLabel("Student Number", Page.GetByLabelOptions().setExact(false)).fill("s$suffix")
        }

        page.getByRole(
            com.microsoft.playwright.options.AriaRole.BUTTON,
            Page.GetByRoleOptions().setName(submitButtonLabel).setExact(false)
        ).click()

        return Credentials(username = username, email = email, password = password)
    }

    protected fun waitForUserByUsername(username: String, retries: Int = 20, waitMillis: Long = 250): User {
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

    protected fun assertActivationEmailSent(email: String, timeoutMs: Long = 10_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val mail = findActivationMail(email)
            if (mail != null) {
                val body = messageBody(mail)
                assertThat(body).contains("/account/activate/user")
                return
            }
            Thread.sleep(100)
        }

        val recoveryJobs = jobExecutionRepository.findByJobType(EmailJobs.Recovery.type)
        assertThat(recoveryJobs)
            .describedAs("Expected activation email, and at least a scheduled recovery email job")
            .isNotEmpty

        throw AssertionError("Expected activation email to be sent to $email")
    }

    private fun findActivationMail(email: String): MimeMessage? {
        return mailSender.outbox.firstOrNull { message ->
            val recipients = (message.allRecipients ?: emptyArray()).map { it.toString() }
            recipients.contains(email) && message.subject == "Activate your Account"
        }
    }

    private fun messageBody(message: MimeMessage): String {
        return when (val content = message.content) {
            is String -> content
            is Multipart -> extractFromMultipart(content)
            else -> content.toString()
        }
    }

    private fun extractFromMultipart(multipart: Multipart): String {
        for (i in 0 until multipart.count) {
            val part = multipart.getBodyPart(i)
            val content = extractFromPart(part)
            if (content != null) return content
        }
        return ""
    }

    private fun extractFromPart(part: Part): String? {
        return when (val content = part.content) {
            is String -> content
            is Multipart -> extractFromMultipart(content)
            else -> null
        }
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

    protected data class Credentials(
        val username: String,
        val email: String,
        val password: String,
    )
}
