package net.blueshell.api.system.frontend.management

import com.microsoft.playwright.Page
import net.blueshell.api.ApiApplication
import net.blueshell.api.config.TestCleanUpListener
import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.systemtests.PlaywrightTestBase
import net.blueshell.systemtests.TestHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestExecutionListeners
import java.net.URI
import java.net.URLDecoder
import java.time.Instant
import java.util.function.Predicate

@Tag("system")
@ActiveProfiles("test")
@TestExecutionListeners(
    listeners = [TestCleanUpListener::class],
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS,
)
@SpringBootTest(
    classes = [ApiApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties = ["server.port=8080", "app.jobs.auto-dispatch=true"],
)
class JobManagerPageSystemTest : PlaywrightTestBase() {

    @Test
    fun `admin can list and retry failed job execution`() {
        val admin = TestHelper.registerActivateAndPromote("ADMIN")
        // Wipe auto-dispatched contact-sync / activation-email rows
        // from the admin registration so the seeded rows are the only
        // ones the manager page sees.
        TestHelper.clearJobExecutions()
        val failedId = TestHelper.createJobExecution(
            jobType = "sync-contact-${System.currentTimeMillis()}",
            status = "FAILED",
            attempts = 2,
            errorType = "RuntimeException",
            errorReason = "Transient failure",
        )
        TestHelper.createJobExecution(
            jobType = "calendar-sync-${System.currentTimeMillis()}",
            status = "SUCCESS",
            attempts = 1,
        )
        val initialQueuedAt = checkNotNull(TestHelper.findJobExecution(failedId)?.queuedAt) {
            "Expected failed execution queuedAt to be set"
        }

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, admin.username, admin.password)
        assertThat(loginStatus).isEqualTo(200)

        val listResponse = page.waitForResponse(
            Predicate { response ->
                response.request().method() == "GET" &&
                    response.url().contains("/management/jobs")
            },
        ) {
            page.navigate("$frontendUrl/management/jobs")
        }
        assertThat(listResponse.status()).isEqualTo(200)

        page.locator("[data-testid='job-row-$failedId']").first().waitFor()
        page.locator("[data-testid='job-row-$failedId']").first().click()

        assertThat(
            page.locator("[data-testid='job-error-reason-$failedId']").innerText(),
        ).contains("Transient failure")

        val retryResponse = page.waitForResponse(
            Predicate { response ->
                response.request().method() == "POST" &&
                    response.url().contains("/management/jobs/$failedId/retry")
            },
        ) {
            page.locator("[data-testid='job-retry-btn-$failedId']").first().click()
        }
        assertThat(retryResponse.status()).isEqualTo(200)

        waitForJob(failedId) { row ->
            row.status == "DEAD" &&
                row.queuedAt != null &&
                row.queuedAt.isAfter(initialQueuedAt)
        }

        val updated = TestHelper.findJobExecution(failedId)!!
        assertThat(updated.status).isEqualTo("DEAD")
        assertThat(updated.queuedAt).isNotNull()
        assertThat(updated.queuedAt).isAfter(initialQueuedAt)
    }

    @Test
    fun `job manager applies frontend filters to backend jobs query`() {
        val admin = TestHelper.registerActivateAndPromote("ADMIN")
        TestHelper.clearJobExecutions()
        val calendarFailedId = TestHelper.createJobExecution(
            jobType = "calendar.sync-${System.currentTimeMillis()}",
            status = "FAILED",
            attempts = 1,
            errorType = "RuntimeException",
            errorReason = "Transient failure in sync",
        )
        val contactQueuedId = TestHelper.createJobExecution(
            jobType = "contact.sync-${System.currentTimeMillis()}",
            status = "QUEUED",
            attempts = 2,
            startedAt = null,
            finishedAt = null,
        )
        val emailSuccessId = TestHelper.createJobExecution(
            jobType = "email.recovery-${System.currentTimeMillis()}",
            status = "SUCCESS",
            attempts = 1,
        )

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, admin.username, admin.password)
        assertThat(loginStatus).isEqualTo(200)

        val initialListResponse = page.waitForResponse(
            Predicate { response ->
                response.request().method() == "GET" &&
                    response.url().contains("/management/jobs")
            },
        ) {
            page.navigate("$frontendUrl/management/jobs")
        }
        assertThat(initialListResponse.status()).isEqualTo(200)
        waitForRows(page, calendarFailedId, contactQueuedId, emailSuccessId)

        val categoryResponse = page.waitForResponse(
            Predicate { response ->
                response.request().method() == "GET" &&
                    response.url().contains("/management/jobs") &&
                    response.url().contains("category=calendar")
            },
        ) {
            selectCategoryFilter(page, "calendar")
        }
        assertThat(categoryResponse.status()).isEqualTo(200)
        val categoryParams = queryParams(categoryResponse.url())
        assertThat(categoryParams["category"]).contains("calendar")
        waitForOnlyCalendar(page, calendarFailedId, contactQueuedId, emailSuccessId)

        val statusResponse = page.waitForResponse(
            Predicate { response ->
                response.request().method() == "GET" &&
                    response.url().contains("/management/jobs") &&
                    response.url().contains("status=FAILED")
            },
        ) {
            selectStatusFilter(page, "failed")
        }
        assertThat(statusResponse.status()).isEqualTo(200)
        val statusParams = queryParams(statusResponse.url())
        assertThat(statusParams["category"]).contains("calendar")
        assertThat(statusParams["status"]).contains("FAILED")
        waitForOnlyCalendar(page, calendarFailedId, contactQueuedId, emailSuccessId)

        val searchResponse = page.waitForResponse(
            Predicate { response ->
                response.request().method() == "GET" &&
                    response.url().contains("/management/jobs") &&
                    response.url().contains("search=")
            },
        ) {
            page.locator("[data-testid='job-filter-search'] input").first().fill("Transient failure")
        }
        assertThat(searchResponse.status()).isEqualTo(200)
        val searchParams = queryParams(searchResponse.url())
        assertThat(searchParams["category"]).contains("calendar")
        assertThat(searchParams["status"]).contains("FAILED")
        assertThat(searchParams["search"]).contains("Transient failure")
        waitForOnlyCalendar(page, calendarFailedId, contactQueuedId, emailSuccessId)
    }

    private fun waitForJob(id: Long, predicate: (TestHelper.JobExecutionRow) -> Boolean) {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            val row = TestHelper.findJobExecution(id)
            if (row != null && predicate(row)) return
            Thread.sleep(200)
        }
        throw AssertionError(
            "Expected job execution $id to satisfy predicate within 30s; last row=${TestHelper.findJobExecution(id)}",
        )
    }

    private fun waitForRows(page: Page, vararg ids: Long) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            if (ids.all { hasJobRow(page, it) }) return
            Thread.sleep(200)
        }
        throw AssertionError("Expected rows ${ids.toList()} to be visible within 10s")
    }

    private fun waitForOnlyCalendar(page: Page, calendarId: Long, contactId: Long, emailId: Long) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            if (hasJobRow(page, calendarId) &&
                !hasJobRow(page, contactId) &&
                !hasJobRow(page, emailId)
            ) {
                return
            }
            Thread.sleep(200)
        }
        throw AssertionError("Expected calendar row $calendarId only after filter; others should be hidden")
    }

    private fun selectCategoryFilter(page: Page, category: String) {
        page.locator("[data-testid='job-filter-category']").first().click()
        page.locator("[data-testid='job-filter-category-option-$category']").first().click()
    }

    private fun selectStatusFilter(page: Page, status: String) {
        page.locator("[data-testid='job-filter-status']").first().click()
        page.locator("[data-testid='job-filter-status-option-$status']").first().click()
    }

    private fun queryParams(url: String): Map<String, List<String>> {
        val query = URI(url).query ?: return emptyMap()
        val values = linkedMapOf<String, MutableList<String>>()
        query.split("&")
            .filter { it.isNotBlank() }
            .forEach { segment ->
                val parts = segment.split("=", limit = 2)
                val key = URLDecoder.decode(parts[0], Charsets.UTF_8)
                val value = URLDecoder.decode(parts.getOrElse(1) { "" }, Charsets.UTF_8)
                values.computeIfAbsent(key) { mutableListOf() }.add(value)
            }
        return values
    }

    private fun hasJobRow(page: Page, id: Long): Boolean {
        return page.locator("[data-testid='job-row-$id']").count() > 0
    }
}
