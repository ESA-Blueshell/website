package net.blueshell.api.system.frontend.management

import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.systemtests.PlaywrightTestBase
import net.blueshell.systemtests.TestHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.function.Predicate

@Tag("system")
class JobManagerStatsSystemTest : PlaywrightTestBase() {

    @Test
    fun `stats panel shows correct counts for real job executions`() {
        val admin = TestHelper.registerActivateAndPromote("ADMIN")
        // POST /users auto-dispatches contact-sync and activation-email
        // jobs that survive on the `job_executions` table and confuse
        // the stats assertion below. Wipe them before seeding the
        // three rows whose counts the test actually checks.
        TestHelper.clearJobExecutions()
        val now = System.currentTimeMillis()
        TestHelper.createJobExecution(jobType = "email.send-$now", status = "SUCCESS")
        TestHelper.createJobExecution(jobType = "calendar.sync-$now", status = "SUCCESS")
        TestHelper.createJobExecution(jobType = "contact.sync-$now", status = "FAILED")

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, admin.username, admin.password)
        assertThat(loginStatus).isEqualTo(200)

        page.waitForResponse(
            Predicate { response ->
                response.request().method() == "GET" &&
                    response.url().contains("/management/jobs/stats")
            },
        ) {
            page.navigate("$frontendUrl/management/jobs")
        }

        page.locator("[data-testid='job-stats-total']").first().waitFor()

        assertThat(page.locator("[data-testid='job-stats-total']").innerText()).contains("3")
        assertThat(page.locator("[data-testid='job-stats-success']").innerText()).contains("2")
        assertThat(page.locator("[data-testid='job-stats-failed']").innerText()).contains("1")
        assertThat(page.locator("[data-testid='job-stats-dead']").innerText()).contains("0")
    }

    @Test
    fun `stats panel runtime section is always visible`() {
        val admin = TestHelper.registerActivateAndPromote("ADMIN")

        val loginStatus = AuthHelper.submitLogin(page, frontendUrl, admin.username, admin.password)
        assertThat(loginStatus).isEqualTo(200)

        page.waitForResponse(
            Predicate { response ->
                response.request().method() == "GET" &&
                    response.url().contains("/management/jobs/stats")
            },
        ) {
            page.navigate("$frontendUrl/management/jobs")
        }

        page.locator("[data-testid='job-stats-runtime']").first().waitFor()

        assertThat(page.locator("[data-testid='job-stats-runtime']").innerText()).contains("Since last startup")
    }
}
