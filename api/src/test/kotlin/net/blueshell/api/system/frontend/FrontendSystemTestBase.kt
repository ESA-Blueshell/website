package net.blueshell.api.system.frontend

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.AriaRole
import jakarta.mail.Multipart
import jakarta.mail.Part
import jakarta.mail.internet.MimeMessage
import net.blueshell.api.ApiApplication
import net.blueshell.api.config.TruncateTestDatabaseListener
import net.blueshell.api.domain.user.persistence.repository.UserRepository
import net.blueshell.api.platform.integration.job.repository.JobExecutionRepository
import net.blueshell.api.platform.integration.mock.MockJavaMailSender
import net.blueshell.api.shared.job.EmailJobs
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestExecutionListeners
import java.util.*

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
abstract class FrontendSystemTestBase @Autowired constructor(
    val userRepository: UserRepository,
    val mailSender: MockJavaMailSender,
    val jobExecutionRepository: JobExecutionRepository,
) {

    @Value($$"${system.frontend.url}")
    lateinit var frontendUrl: String

    private lateinit var playwright: Playwright
    private lateinit var browser: Browser

    @BeforeAll
    fun setUpPlaywright() {
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
    }

    protected fun withPage(block: (Page) -> Unit) {
        val context = browser.newContext()
        val page = context.newPage()
        context.use {
            block(page)
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
            AriaRole.BUTTON,
            Page.GetByRoleOptions().setName(submitButtonLabel).setExact(false)
        ).click()

        return Credentials(username = username, email = email, password = password)
    }

    protected fun <T> waitForOptional(
        producer: () -> Optional<T>,
        retries: Int = 10,
        waitMillis: Long = 100,
        onTimeoutMessage: () -> String = { "Expected value to be available" }
    ): T {
        repeat(retries) { attempt ->
            val found = producer()
            if (found.isPresent) return found.get()

            if (attempt < retries - 1) Thread.sleep(waitMillis)
        }
        throw AssertionError(onTimeoutMessage())
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

    protected data class Credentials(
        val username: String,
        val email: String,
        val password: String,
    )
}
