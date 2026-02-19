package net.blueshell.api.system.frontend

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.AriaRole
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.mail.internet.MimeMessage
import net.blueshell.api.ApiApplication
import net.blueshell.api.config.TestCleanUpListener
import net.blueshell.api.domain.user.persistence.repository.UserRepository
import net.blueshell.api.platform.integration.mock.MockJavaMailSender
import net.blueshell.api.system.frontend.helper.UserFormHelper
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestExecutionListeners
import java.io.File
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestExecutionListeners(
    listeners = [TestCleanUpListener::class],
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS
)
@SpringBootTest(
    classes = [ApiApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties = ["server.port=8080"]
)
abstract class FrontendSystemTestBase {

    @Value($$"${system.frontend.url}")
    lateinit var frontendUrl: String

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var mailSender: MockJavaMailSender

    private lateinit var playwright: Playwright
    private lateinit var browser: Browser
    private val objectMapper = ObjectMapper()
    private val coverageArtifactsWritten = AtomicInteger(0)
    private val frontendCoverageEnabled = System.getProperty("frontend.coverage.enabled", "false").toBoolean()
    private val frontendCoverageRequired = System.getProperty("frontend.coverage.required", "false").toBoolean()
    private val frontendCoverageRawDir = File(System.getProperty("frontend.coverage.rawDir", "build/coverage/frontend-system/raw"))

    @BeforeAll
    fun setUpPlaywright() {
        if (frontendCoverageEnabled) {
            prepareCoverageOutputDir()
        }
        playwright = Playwright.create()
        browser = playwright.chromium().launch(
            BrowserType.LaunchOptions().setHeadless(true)
        )
    }

    @BeforeEach
    fun clearOutbox() {
        mailSender.clear()
    }

    @AfterAll
    fun tearDownPlaywright() {
        browser.close()
        playwright.close()

        if (frontendCoverageEnabled && frontendCoverageRequired && coverageArtifactsWritten.get() == 0) {
            throw AssertionError(
                "Frontend coverage collection was enabled, but no browser coverage artifacts were captured. " +
                        "Ensure the frontend runs with VITE_COVERAGE=true."
            )
        }
    }

    protected fun withPage(block: (Page) -> Unit) {
        val context = browser.newContext()
        val page = context.newPage()
        try {
            block(page)
        } finally {
            if (frontendCoverageEnabled) {
                captureFrontendCoverage(context.pages())
            }
            context.close()
        }
    }

    private fun prepareCoverageOutputDir() {
        frontendCoverageRawDir.mkdirs()
        frontendCoverageRawDir
            .listFiles()
            ?.filter { it.isFile && it.extension == "json" }
            ?.forEach { it.delete() }
    }

    private fun captureFrontendCoverage(pages: List<Page>) {
        pages.forEach { page ->
            if (page.isClosed) return@forEach
            val coverage = runCatching {
                page.evaluate("() => globalThis.__coverage__ || null")
            }.getOrNull() ?: return@forEach

            val filename = "frontend-coverage-${System.currentTimeMillis()}-${UUID.randomUUID()}.json"
            val target = File(frontendCoverageRawDir, filename)
            objectMapper.writeValue(target, coverage)
            coverageArtifactsWritten.incrementAndGet()
        }
    }

    protected fun createAccountThroughUi(
        page: Page,
        url: String,
        submitButtonLabel: String,
        includeMemberProfile: Boolean
    ): Credentials {
        val suffix = System.currentTimeMillis().toString().takeLast(8)
        val username = "sysuser$suffix"
        val email = "sysuser$suffix@example.com"
        val password = "Passw0rd!$suffix"
        val phoneNumber = "+3161${suffix.takeLast(7)}"

        page.navigate(url)
        UserFormHelper.fill(
            page = page,
            fields = UserFormHelper.Fields(
                initials = "SU",
                firstName = "System",
                surname = "User$suffix",
                username = username,
                discord = "sysuser$suffix",
                email = email,
                phoneNumber = phoneNumber,
                password = password,
                repeatedPassword = password,
                dateOfBirth = if (includeMemberProfile) "1999-04-12" else null,
                gender = if (includeMemberProfile) "X" else null,
                studentNumber = if (includeMemberProfile) "s$suffix" else null
            )
        )

        page.getByRole(
            AriaRole.BUTTON,
            Page.GetByRoleOptions().setName(submitButtonLabel).setExact(false)
        ).click()

        waitForOptional(
            producer = { userRepository.findByUsername(username) },
            onTimeoutMessage = { "Expected account creation to persist user '$username'" }
        )

        return Credentials(username = username, email = email, password = password)
    }

    protected fun waitFor(
        timeoutMs: Long = 6_000,
        intervalMs: Long = 200,
        onTimeoutMessage: () -> String = { "Expected condition to become true" },
        condition: () -> Boolean
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(intervalMs)
        }
        throw AssertionError(onTimeoutMessage())
    }

    protected fun <T : Any> waitForOptional(
        producer: () -> Optional<T>,
        retries: Int = 30,
        waitMillis: Long = 200,
        onTimeoutMessage: () -> String = { "Expected value to be available" }
    ): T {
        var found = Optional.empty<T>()
        waitFor(
            timeoutMs = retries.toLong() * waitMillis,
            intervalMs = waitMillis,
            onTimeoutMessage = onTimeoutMessage
        ) {
            found = producer()
            found.isPresent
        }
        return found.get()
    }

    protected fun assertEmailSent(
        recipientEmail: String,
        subject: String,
        timeoutMs: Long = 10_000
    ) {
        waitFor(
            timeoutMs = timeoutMs,
            intervalMs = 100,
            onTimeoutMessage = { "Expected email '$subject' to be sent to $recipientEmail" }
        ) {
            findEmail(recipientEmail, subject) != null
        }
    }

    protected fun findEmail(
        recipientEmail: String,
        subject: String
    ): MimeMessage? {
        return mailSender.outbox.firstOrNull { message ->
            val recipients = (message.allRecipients ?: emptyArray()).map { it.toString() }
            recipients.contains(recipientEmail) && message.subject == subject
        }
    }

    protected data class Credentials(
        val username: String,
        val email: String,
        val password: String,
    )
}
