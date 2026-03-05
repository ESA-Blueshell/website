package net.blueshell.api.system.frontend.management

import net.blueshell.api.factory.job.persistence.JobExecutionFactory
import net.blueshell.api.factory.user.persistence.UserFactory
import net.blueshell.api.platform.integration.job.persistence.repository.JobExecutionRepository
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
class JobManagerStatsSystemTest : FrontendSystemTestBase() {

    @Autowired
    private lateinit var userFactory: UserFactory

    @Autowired
    private lateinit var jobExecutionFactory: JobExecutionFactory

    @Autowired
    private lateinit var jobExecutionRepository: JobExecutionRepository

    @Test
    fun `stats panel shows correct counts for real job executions`() {
        val admin = userFactory.createUserWithRole(Role.ADMIN, enabled = true)
        createJobExecution(jobType = "email.send-${System.currentTimeMillis()}", status = JobExecutionStatus.SUCCESS)
        createJobExecution(jobType = "calendar.sync-${System.currentTimeMillis()}", status = JobExecutionStatus.SUCCESS)
        createJobExecution(jobType = "contact.sync-${System.currentTimeMillis()}", status = JobExecutionStatus.FAILED)

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, admin.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            page.waitForResponse(
                Predicate { response ->
                    response.request().method() == "GET" &&
                        response.url().contains("/management/jobs/stats")
                }
            ) {
                page.navigate("$frontendUrl/management/jobs")
            }

            waitFor(
                onTimeoutMessage = { "Expected stats panel to be visible with correct counts" }
            ) {
                page.locator("[data-testid='job-stats-total']").count() > 0
            }

            assertThat(
                page.locator("[data-testid='job-stats-total']").innerText()
            ).contains("3")

            assertThat(
                page.locator("[data-testid='job-stats-success']").innerText()
            ).contains("2")

            assertThat(
                page.locator("[data-testid='job-stats-failed']").innerText()
            ).contains("1")

            assertThat(
                page.locator("[data-testid='job-stats-dead']").innerText()
            ).contains("0")
        }
    }

    @Test
    fun `stats panel runtime section is always visible`() {
        val admin = userFactory.createUserWithRole(Role.ADMIN, enabled = true)

        withPage { page ->
            val loginStatus = AuthHelper.submitLogin(page, frontendUrl, admin.username, DEFAULT_PASSWORD)
            assertThat(loginStatus).isEqualTo(200)

            page.waitForResponse(
                Predicate { response ->
                    response.request().method() == "GET" &&
                        response.url().contains("/management/jobs/stats")
                }
            ) {
                page.navigate("$frontendUrl/management/jobs")
            }

            waitFor(
                onTimeoutMessage = { "Expected runtime stats section to be visible" }
            ) {
                page.locator("[data-testid='job-stats-runtime']").count() > 0
            }

            assertThat(
                page.locator("[data-testid='job-stats-runtime']").innerText()
            ).contains("Since last startup")
        }
    }

    private fun createJobExecution(
        jobType: String,
        status: JobExecutionStatus
    ) {
        val execution = jobExecutionFactory.create(jobType = jobType)
        execution.status = status
        execution.queuedAt = Instant.now().minusSeconds(600)
        execution.startedAt = if (status != JobExecutionStatus.QUEUED) Instant.now().minusSeconds(300) else null
        execution.finishedAt = if (status == JobExecutionStatus.SUCCESS || status == JobExecutionStatus.FAILED) {
            Instant.now().minusSeconds(120)
        } else {
            null
        }
        jobExecutionRepository.saveAndFlush(execution)
    }

    private companion object {
        const val DEFAULT_PASSWORD = "Password123!"
    }
}
