package net.blueshell.api.alerts

import net.blueshell.api.exceptions.api.ExceptionConcern
import net.blueshell.api.exceptions.api.ExceptionRecorder
import net.blueshell.api.exceptions.api.ExceptionSource
import net.blueshell.api.exceptions.domain.RecordedExceptions
import net.blueshell.api.exceptions.persistence.RecordedExceptionRepository
import net.blueshell.api.shared.enums.JobExecutionStatus
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
class AlertsIT : UserTestSupport() {
    @Autowired
    private lateinit var recorder: ExceptionRecorder

    @Autowired
    private lateinit var faults: RecordedExceptions

    @Autowired
    private lateinit var records: RecordedExceptionRepository

    @BeforeEach
    fun noFaults() {
        records.deleteAll()
    }

    private fun alertsOf(reader: User): List<Map<String, Any?>> {
        val body =
            mvc
                .perform(get("/management/alerts").with(signedIn(reader)))
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString
        @Suppress("UNCHECKED_CAST")
        return mapper.readValue(body, List::class.java) as List<Map<String, Any?>>
    }

    private fun keysOf(reader: User) = alertsOf(reader).map { it["key"] as String }

    private fun post(
        reader: User,
        path: String,
        key: String,
    ) = mvc.perform(
        post("/management/alerts/$path")
            .with(signedIn(reader))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"key":"$key"}"""),
    )

    @Test
    fun `a dead job raises an alert for admins that clears once it is retried`() {
        val admin = createUserWithRole(Role.ADMIN)
        val dead = createJobExecutionFixture(status = JobExecutionStatus.DEAD)

        assertThat(keysOf(admin)).contains("job-dead")

        transactionTemplate.executeWithoutResult {
            val job = jobExecutions.findById(dead.id!!).orElseThrow()
            job.status = JobExecutionStatus.QUEUED
        }

        assertThat(keysOf(admin)).doesNotContain("job-dead")
    }

    @Test
    fun `an open exception raises an alert for admins that clears once it is resolved`() {
        val admin = createUserWithRole(Role.ADMIN)
        recorder.record(IllegalStateException("broke"), ExceptionConcern(ExceptionSource.JOB, "contact.sync", null))
        val fault = faults.list(resolved = false).single()

        assertThat(alertsOf(admin).map { it["kind"] }).contains("EXCEPTION_OPEN")

        faults.resolve(fault.id!!)

        assertThat(alertsOf(admin).map { it["kind"] }).doesNotContain("EXCEPTION_OPEN")
    }

    @Test
    fun `admin-only alerts never show to the board or the treasurer`() {
        createJobExecutionFixture(status = JobExecutionStatus.DEAD)
        recorder.record(IllegalStateException("broke"), ExceptionConcern(ExceptionSource.JOB, "contact.sync", null))
        val adminOnly = setOf("JOB_DEAD", "EXCEPTION_OPEN")

        for (role in listOf(Role.BOARD, Role.TREASURER)) {
            assertThat(alertsOf(createUserWithRole(role)).map { it["kind"] }).doesNotContainAnyElementsOf(adminOnly)
        }
    }

    @Test
    fun `hiding an alert hides it for that person only, until they show it again`() {
        val first = createUserWithRole(Role.ADMIN)
        val second = createUserWithRole(Role.ADMIN)
        createJobExecutionFixture(status = JobExecutionStatus.DEAD)
        val key = "job-dead"

        post(first, "hidden", key).andExpect(status().isNoContent)
        post(first, "hidden", key).andExpect(status().isNoContent)

        assertThat(alertsOf(first).single { it["key"] == key }["hidden"]).isEqualTo(true)
        assertThat(alertsOf(second).single { it["key"] == key }["hidden"]).isEqualTo(false)

        post(first, "shown", key).andExpect(status().isNoContent)
        assertThat(alertsOf(first).single { it["key"] == key }["hidden"]).isEqualTo(false)
    }

    @Test
    fun `a hidden alert stays hidden when its newest cause is dealt with, and shows again on a new one`() {
        val admin = createUserWithRole(Role.ADMIN)
        createJobExecutionFixture(status = JobExecutionStatus.DEAD)
        val newest = createJobExecutionFixture(status = JobExecutionStatus.DEAD)
        post(admin, "hidden", "job-dead").andExpect(status().isNoContent)

        transactionTemplate.executeWithoutResult {
            jobExecutions.findById(newest.id!!).orElseThrow().status = JobExecutionStatus.QUEUED
        }
        assertThat(alertsOf(admin).single { it["key"] == "job-dead" }["hidden"]).isEqualTo(true)

        createJobExecutionFixture(status = JobExecutionStatus.DEAD)
        assertThat(alertsOf(admin).single { it["key"] == "job-dead" }["hidden"]).isEqualTo(false)
    }

    @Test
    fun `an alert the reader cannot see cannot be hidden, and members cannot read alerts`() {
        val board = createUserWithRole(Role.BOARD)
        createJobExecutionFixture(status = JobExecutionStatus.DEAD)
        val key = "job-dead"

        post(board, "hidden", key).andExpect(status().isNotFound)
        mvc.perform(get("/management/alerts").with(signedIn(createUserWithRole(Role.MEMBER)))).andExpect(status().isForbidden)
        mvc.perform(get("/management/alerts")).andExpect(status().isUnauthorized)
    }
}
