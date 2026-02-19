package net.blueshell.api.system.frontend.management

import net.blueshell.api.factory.job.persistence.JobExecutionFactory
import net.blueshell.api.factory.user.persistence.UserFactory
import net.blueshell.api.platform.integration.job.model.JobExecution
import net.blueshell.api.platform.integration.job.repository.JobExecutionRepository
import net.blueshell.api.shared.enums.JobExecutionStatus
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.system.frontend.FrontendSystemTestBase
import net.blueshell.api.system.frontend.helper.AuthHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
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
                page.getByText(failed.jobType, com.microsoft.playwright.Page.GetByTextOptions().setExact(true)).count() > 0
            }

            assertThat(
                page.locator("[data-testid='job-error-type-$failedId']").innerText()
            ).contains("RuntimeException")

            val retryResponse = page.waitForResponse(
                Predicate { response ->
                    response.request().method() == "POST" &&
                        response.url().contains("/management/jobs/$failedId/retry")
                }
            ) {
                page.getByRole(
                    com.microsoft.playwright.options.AriaRole.BUTTON,
                    com.microsoft.playwright.Page.GetByRoleOptions().setName("Retry").setExact(true)
                ).first().click()
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

    private companion object {
        const val DEFAULT_PASSWORD = "Password123!"
    }
}
