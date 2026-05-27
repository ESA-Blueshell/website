package net.blueshell.api.platform.integration.job.web

import net.blueshell.api.shared.enums.ActionActorType
import net.blueshell.api.shared.enums.JobExecutionStatus
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
class JobManagementControllerIT : UserTestSupport() {

    @Nested
    inner class List {
        @Test
        fun `admin lists jobs with paging metadata`() {
            val admin = createUserWithRole(Role.ADMIN)
            val actor = createUserWithRole(Role.MEMBER)
            val event = createEventFixture(title = "Controller IT event ${System.currentTimeMillis()}")
            val first = createJobExecutionFixture(jobType = "job.a")
            val second = createJobExecutionFixture(jobType = "job.b")
            first.payload = """{"eventId":${event.id}}"""
            first.initiatedByUserId = actor.id
            first.initiatedByType = ActionActorType.USER
            first.initiatedByRole = Role.MEMBER
            jobExecutions.saveAndFlush(first)

            mvc.perform(get("/management/jobs").with(bearer(admin)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[0].id").isNumber)
                .andExpect(jsonPath("$.content[0].payload").doesNotExist())
                .andExpect(jsonPath("$.content[0].initiatedByDisplay").isString)
                .andExpect(jsonPath("$.content[*].relatedEntities").isArray)
                .andExpect(jsonPath("$.content[*].id").value(org.hamcrest.Matchers.hasItems(first.id!!.toInt(), second.id!!.toInt())))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(50))
        }

        @Test
        fun `admin filters jobs by status and category`() {
            val admin = createUserWithRole(Role.ADMIN)
            val matching = createJobExecutionFixture(jobType = "calendar.sync")
            matching.status = JobExecutionStatus.FAILED
            jobExecutions.saveAndFlush(matching)

            val differentCategory = createJobExecutionFixture(jobType = "contact.sync")
            differentCategory.status = JobExecutionStatus.FAILED
            jobExecutions.saveAndFlush(differentCategory)

            val differentStatus = createJobExecutionFixture(jobType = "calendar.add")
            differentStatus.status = JobExecutionStatus.SUCCESS
            jobExecutions.saveAndFlush(differentStatus)

            mvc.perform(
                get("/management/jobs")
                    .queryParam("status", "FAILED")
                    .queryParam("category", "calendar")
                    .with(bearer(admin))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(matching.id!!.toInt()))
                .andExpect(jsonPath("$.content[0].status").value("FAILED"))
                .andExpect(jsonPath("$.content[0].category").value("calendar"))
        }

        @Test
        fun `admin filters jobs by search initiatedByType and jobType`() {
            val admin = createUserWithRole(Role.ADMIN)
            val actor = createUserWithRole(Role.MEMBER)

            val matching = createJobExecutionFixture(jobType = "calendar.sync-user")
            matching.status = JobExecutionStatus.FAILED
            matching.errorReason = "Recurring sync mismatch"
            matching.initiatedByType = ActionActorType.USER
            matching.initiatedByRole = Role.MEMBER
            matching.initiatedByUserId = actor.id
            jobExecutions.saveAndFlush(matching)

            val wrongSearch = createJobExecutionFixture(jobType = "calendar.sync-alt")
            wrongSearch.status = JobExecutionStatus.FAILED
            wrongSearch.errorReason = "Different failure text"
            wrongSearch.initiatedByType = ActionActorType.USER
            wrongSearch.initiatedByRole = Role.MEMBER
            wrongSearch.initiatedByUserId = actor.id
            jobExecutions.saveAndFlush(wrongSearch)

            val wrongActorType = createJobExecutionFixture(jobType = "calendar.sync-system")
            wrongActorType.status = JobExecutionStatus.FAILED
            wrongActorType.errorReason = "Recurring sync mismatch"
            wrongActorType.initiatedByType = ActionActorType.SYSTEM
            wrongActorType.initiatedByRole = Role.SYSTEM
            wrongActorType.initiatedByUserId = null
            jobExecutions.saveAndFlush(wrongActorType)

            mvc.perform(
                get("/management/jobs")
                    .queryParam("search", "Recurring sync mismatch")
                    .queryParam("initiatedByType", "USER")
                    .queryParam("jobType", "calendar.sync")
                    .with(bearer(admin))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(matching.id!!.toInt()))
                .andExpect(jsonPath("$.content[0].jobType").value("calendar.sync-user"))
                .andExpect(jsonPath("$.content[0].initiatedByType").value("USER"))
                .andExpect(jsonPath("$.content[0].payload").doesNotExist())
        }

        @Test
        fun `admin receives 50 items per page`() {
            val admin = createUserWithRole(Role.ADMIN)
            repeat(51) { index ->
                createJobExecutionFixture(jobType = "job.page.$index")
            }

            mvc.perform(
                get("/management/jobs")
                    .queryParam("page", "0")
                    .with(bearer(admin))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(50))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(50))
                .andExpect(jsonPath("$.page.totalElements").value(51))
                .andExpect(jsonPath("$.page.totalPages").value(2))

            mvc.perform(
                get("/management/jobs")
                    .queryParam("page", "1")
                    .with(bearer(admin))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page.number").value(1))
        }
    }

    @Nested
    inner class Stats {
        @Test
        fun `stats returns correct db counts per status`() {
            val admin = createUserWithRole(Role.ADMIN)
            createJobExecutionFixture(status = JobExecutionStatus.SUCCESS)
            createJobExecutionFixture(status = JobExecutionStatus.SUCCESS)
            createJobExecutionFixture(status = JobExecutionStatus.FAILED)
            createJobExecutionFixture(status = JobExecutionStatus.DEAD)

            mvc.perform(get("/management/jobs/stats").with(bearer(admin)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.totalCount").value(4))
                .andExpect(jsonPath("$.successCount").value(2))
                .andExpect(jsonPath("$.failedCount").value(1))
                .andExpect(jsonPath("$.deadCount").value(1))
                .andExpect(jsonPath("$.queuedCount").value(0))
        }

        @Test
        fun `stats runtime metrics are non-negative`() {
            val admin = createUserWithRole(Role.ADMIN)

            mvc.perform(get("/management/jobs/stats").with(bearer(admin)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.deadSinceStartup").isNumber)
                .andExpect(jsonPath("$.failedSinceStartup").isNumber)
                .andExpect(jsonPath("$.recoveriesSinceStartup").isNumber)
                .andExpect(jsonPath("$.avgSuccessDurationSeconds").isNumber)
        }

        @Test
        fun `stats empty database returns all zeros`() {
            val admin = createUserWithRole(Role.ADMIN)

            mvc.perform(get("/management/jobs/stats").with(bearer(admin)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.successCount").value(0))
        }
    }

    @Nested
    inner class Retry {
        @Test
        fun `admin retries failed job`() {
            val admin = createUserWithRole(Role.ADMIN)
            val job = createJobExecutionFixture(jobType = "retry-target")
            job.status = JobExecutionStatus.FAILED
            jobExecutions.saveAndFlush(job)

            mvc.perform(post("/management/jobs/{id}/retry", job.id).with(bearer(admin)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(job.id))
                .andExpect(jsonPath("$.jobType").value("retry-target"))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.payload").doesNotExist())
                .andExpect(jsonPath("$.attempts").value(1))

            val reloaded = jobExecutions.findById(job.id!!).orElseThrow()
            assertThat(reloaded.attempts).isEqualTo(1)
            assertThat(reloaded.nextAttemptAt).isNull()
        }

        @Test
        fun `admin retries dead job`() {
            val admin = createUserWithRole(Role.ADMIN)
            val job = createJobExecutionFixture(jobType = "dead-retry-target")
            job.status = JobExecutionStatus.DEAD
            jobExecutions.saveAndFlush(job)

            mvc.perform(post("/management/jobs/{id}/retry", job.id).with(bearer(admin)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("QUEUED"))
        }

        @Test
        fun `retry supersedes other jobs of the same kind and args`() {
            val admin = createUserWithRole(Role.ADMIN)

            val target = createJobExecutionFixture(jobType = "supersede-target")
            target.status = JobExecutionStatus.FAILED
            target.dedupKey = "user-1"
            jobExecutions.saveAndFlush(target)

            val sibling = createJobExecutionFixture(jobType = "supersede-target")
            sibling.status = JobExecutionStatus.FAILED
            sibling.dedupKey = "user-1"
            jobExecutions.saveAndFlush(sibling)

            val differentArgs = createJobExecutionFixture(jobType = "supersede-target")
            differentArgs.status = JobExecutionStatus.FAILED
            differentArgs.dedupKey = "user-2"
            jobExecutions.saveAndFlush(differentArgs)

            mvc.perform(post("/management/jobs/{id}/retry", target.id).with(bearer(admin)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("QUEUED"))

            assertThat(jobExecutions.findById(sibling.id!!).orElseThrow().status)
                .isEqualTo(JobExecutionStatus.DEAD)
            assertThat(jobExecutions.findById(differentArgs.id!!).orElseThrow().status)
                .isEqualTo(JobExecutionStatus.FAILED)
        }

        @Test
        fun `retry supersedes dedup-less siblings matched by payload`() {
            val admin = createUserWithRole(Role.ADMIN)

            // dedupKey left null on purpose: these must match by payload, which
            // requires the native LONGTEXT comparison (a derived query never matches).
            val target = createJobExecutionFixture(jobType = "payload-supersede")
            target.status = JobExecutionStatus.FAILED
            target.payload = """{"userId":42}"""
            jobExecutions.saveAndFlush(target)

            val sibling = createJobExecutionFixture(jobType = "payload-supersede")
            sibling.status = JobExecutionStatus.FAILED
            sibling.payload = """{"userId":42}"""
            jobExecutions.saveAndFlush(sibling)

            val differentArgs = createJobExecutionFixture(jobType = "payload-supersede")
            differentArgs.status = JobExecutionStatus.FAILED
            differentArgs.payload = """{"userId":99}"""
            jobExecutions.saveAndFlush(differentArgs)

            mvc.perform(post("/management/jobs/{id}/retry", target.id).with(bearer(admin)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("QUEUED"))

            assertThat(jobExecutions.findById(sibling.id!!).orElseThrow().status)
                .isEqualTo(JobExecutionStatus.DEAD)
            assertThat(jobExecutions.findById(differentArgs.id!!).orElseThrow().status)
                .isEqualTo(JobExecutionStatus.FAILED)
        }

        @Test
        fun `admin cannot retry queued job`() {
            val admin = createUserWithRole(Role.ADMIN)
            val job = createJobExecutionFixture(jobType = "queued-target")

            mvc.perform(post("/management/jobs/{id}/retry", job.id).with(bearer(admin)))
                .andExpect(status().isBadRequest)
        }
    }
}
