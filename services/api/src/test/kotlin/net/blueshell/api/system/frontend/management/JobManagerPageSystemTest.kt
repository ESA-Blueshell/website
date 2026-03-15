package net.blueshell.api.system.frontend.management

import com.microsoft.playwright.Page
import net.blueshell.api.factory.job.persistence.JobExecutionFactory
import net.blueshell.api.factory.user.persistence.UserFactory
import net.blueshell.api.platform.integration.job.persistence.JobExecution
import net.blueshell.api.platform.integration.job.persistence.repository.JobExecutionRepository
import net.blueshell.api.shared.enums.JobExecutionStatus
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.system.frontend.FrontendSystemTestBase
import net.blueshell.api.system.frontend.helper.AuthHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.net.URI
import java.net.URLDecoder
import java.time.Instant
import java.util.function.Predicate

@Tag("system")
class JobManagerPageSystemTest : FrontendSystemTestBase() {

    @Autowired
    private lateinit var userFactory: UserFactory

    @Autowired
    private lateinit var jobExecutionFactory: JobExecutionFactory

    @Autowired
    private lateinit var jobExecutionRepository: JobExecutionRepository

    @Test
    fun `admin can list and retry failed job execution`() {
        val admin = userFactory.createUserWithRole(Role.ADMIN, enabled = true)
        val failed = createJobExecution(
            jobType = "sync-contact-${System.currentTimeMillis()}",
            status = JobExecutionStatus.FAILED,
            attempts = 2,
            errorType = "RuntimeException",
            errorReason = "Transient failure"
        )
        createJobExecution(
            jobType = "calendar-sync-${System.currentTimeMillis()}",
            status = JobExecutionStatus.SUCCESS,
            attempts = 1
        )

        val failedId = checkNotNull(failed.id) { "Expected failed execution id" }
        val initialQueuedAt = checkNotNull(failed.queuedAt) { "Expected failed execution queuedAt to be set" }

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, admin.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            val listResponse = page.waitForResponse(
                Predicate { response ->
                    response.request().method() == "GET" &&
                        response.url().contains("/management/jobs")
                }
            ) {
                page.navigate("$frontendUrl/management/jobs")
            }
            assertThat(listResponse.status()).isEqualTo(200)

            waitFor(
                onTimeoutMessage = { "Expected failed job type ${failed.jobType} to appear in job manager table" }
            ) {
                page.locator("[data-testid='job-row-$failedId']").count() > 0
            }

            page.locator("[data-testid='job-row-$failedId']").first().click()

            assertThat(
                page.locator("[data-testid='job-error-reason-$failedId']").innerText()
            ).contains("Transient failure")

            val retryResponse = page.waitForResponse(
                Predicate { response ->
                    response.request().method() == "POST" &&
                        response.url().contains("/management/jobs/$failedId/retry")
                }
            ) {
                page.locator("[data-testid='job-retry-btn-$failedId']").first().click()
            }
            assertThat(retryResponse.status()).isEqualTo(200)

            waitFor(
                onTimeoutMessage = { "Expected failed job $failedId retry to increment attempts and refresh queuedAt" }
            ) {
                val updated = jobExecutionRepository.findById(failedId).orElseThrow()
                updated.attempts == 3 &&
                    updated.queuedAt != null &&
                    updated.queuedAt!!.isAfter(initialQueuedAt)
            }

            val updated = jobExecutionRepository.findById(failedId).orElseThrow()
            assertThat(updated.attempts).isEqualTo(3)
            assertThat(updated.queuedAt).isNotNull()
            assertThat(updated.queuedAt).isAfter(initialQueuedAt)
        }
    }

    @Test
    fun `job manager applies frontend filters to backend jobs query`() {
        val admin = userFactory.createUserWithRole(Role.ADMIN, enabled = true)
        val calendarFailed = createJobExecution(
            jobType = "calendar.sync-${System.currentTimeMillis()}",
            status = JobExecutionStatus.FAILED,
            attempts = 1,
            errorType = "RuntimeException",
            errorReason = "Transient failure in sync"
        )
        val contactQueued = createJobExecution(
            jobType = "contact.sync-${System.currentTimeMillis()}",
            status = JobExecutionStatus.QUEUED,
            attempts = 2
        )
        val emailSuccess = createJobExecution(
            jobType = "email.recovery-${System.currentTimeMillis()}",
            status = JobExecutionStatus.SUCCESS,
            attempts = 1
        )
        val calendarFailedId = checkNotNull(calendarFailed.id) { "Expected calendar failed execution id" }
        val contactQueuedId = checkNotNull(contactQueued.id) { "Expected contact queued execution id" }
        val emailSuccessId = checkNotNull(emailSuccess.id) { "Expected email success execution id" }

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, admin.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            val initialListResponse = page.waitForResponse(
                Predicate { response ->
                    response.request().method() == "GET" &&
                        response.url().contains("/management/jobs")
                }
            ) {
                page.navigate("$frontendUrl/management/jobs")
            }
            assertThat(initialListResponse.status()).isEqualTo(200)
            waitFor(
                onTimeoutMessage = {
                    "Expected created job rows to be visible before applying filters"
                }
            ) {
                hasJobRow(page, calendarFailedId) &&
                    hasJobRow(page, contactQueuedId) &&
                    hasJobRow(page, emailSuccessId)
            }

            val categoryResponse = page.waitForResponse(
                Predicate { response ->
                    response.request().method() == "GET" &&
                        response.url().contains("/management/jobs") &&
                        response.url().contains("category=calendar")
                }
            ) {
                selectCategoryFilter(page, "calendar")
            }
            assertThat(categoryResponse.status()).isEqualTo(200)
            val categoryParams = queryParams(categoryResponse.url())
            assertThat(categoryParams["category"]).contains("calendar")
            waitFor(
                onTimeoutMessage = {
                    "Expected only calendar rows to remain after category filter"
                }
            ) {
                hasJobRow(page, calendarFailedId) &&
                    !hasJobRow(page, contactQueuedId) &&
                    !hasJobRow(page, emailSuccessId)
            }

            val statusResponse = page.waitForResponse(
                Predicate { response ->
                    response.request().method() == "GET" &&
                        response.url().contains("/management/jobs") &&
                        response.url().contains("status=FAILED")
                }
            ) {
                selectStatusFilter(page, "failed")
            }
            assertThat(statusResponse.status()).isEqualTo(200)
            val statusParams = queryParams(statusResponse.url())
            assertThat(statusParams["category"]).contains("calendar")
            assertThat(statusParams["status"]).contains("FAILED")
            waitFor(
                onTimeoutMessage = {
                    "Expected only failed calendar row to remain after status filter"
                }
            ) {
                hasJobRow(page, calendarFailedId) &&
                    !hasJobRow(page, contactQueuedId) &&
                    !hasJobRow(page, emailSuccessId)
            }

            val searchResponse = page.waitForResponse(
                Predicate { response ->
                    response.request().method() == "GET" &&
                        response.url().contains("/management/jobs") &&
                        response.url().contains("search=")
                }
            ) {
                page.locator("[data-testid='job-filter-search'] input").first().fill("Transient failure")
            }
            assertThat(searchResponse.status()).isEqualTo(200)
            val searchParams = queryParams(searchResponse.url())
            assertThat(searchParams["category"]).contains("calendar")
            assertThat(searchParams["status"]).contains("FAILED")
            assertThat(searchParams["search"]).contains("Transient failure")
            waitFor(
                onTimeoutMessage = {
                    "Expected search filter to keep matching failed calendar row only"
                }
            ) {
                hasJobRow(page, calendarFailedId) &&
                    !hasJobRow(page, contactQueuedId) &&
                    !hasJobRow(page, emailSuccessId)
            }
        }
    }

    private fun createJobExecution(
        jobType: String,
        status: JobExecutionStatus,
        attempts: Int,
        errorType: String? = null,
        errorReason: String? = null
    ): JobExecution {
        val execution = jobExecutionFactory.create(jobType = jobType)
        execution.status = status
        execution.attempts = attempts
        execution.queuedAt = Instant.now().minusSeconds(600)
        execution.startedAt = if (status != JobExecutionStatus.QUEUED) Instant.now().minusSeconds(300) else null
        execution.finishedAt = if (status == JobExecutionStatus.SUCCESS || status == JobExecutionStatus.FAILED) {
            Instant.now().minusSeconds(120)
        } else {
            null
        }
        execution.errorType = errorType
        execution.errorReason = errorReason
        execution.errorMessage = if (errorType != null && errorReason != null) "$errorType: $errorReason" else null
        return jobExecutionRepository.saveAndFlush(execution)
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

    private companion object {
        const val DEFAULT_PASSWORD = "Password123!"
    }
}
