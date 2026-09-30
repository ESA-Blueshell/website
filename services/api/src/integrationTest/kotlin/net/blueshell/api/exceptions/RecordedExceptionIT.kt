package net.blueshell.api.exceptions

import net.blueshell.api.exceptions.api.ExceptionConcern
import net.blueshell.api.exceptions.api.ExceptionRecorder
import net.blueshell.api.exceptions.api.ExceptionSource
import net.blueshell.api.exceptions.domain.RecordedExceptions
import net.blueshell.api.exceptions.persistence.RecordedExceptionRepository
import net.blueshell.api.platform.config.advice.ExceptionRecordingFilter
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestComponent
import org.springframework.context.annotation.Import
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.WebApplicationContext
import java.time.Duration

@SpringBootTest
@Import(RecordedExceptionIT.Failing::class)
class RecordedExceptionIT : UserTestSupport() {
    /** A route that fails in two places, so faults can be told apart by where they were thrown. */
    @TestComponent
    @RestController
    class Failing {
        @GetMapping("/test-only/fails/{id}")
        fun fails(
            @PathVariable id: Long,
        ): String = throw IllegalStateException("broke $id")

        @GetMapping("/test-only/fails-elsewhere")
        fun failsElsewhere(): String = throw IllegalStateException("broke elsewhere")
    }

    @Autowired
    private lateinit var records: RecordedExceptionRepository

    @Autowired
    private lateinit var recorder: ExceptionRecorder

    @Autowired
    private lateinit var faults: RecordedExceptions

    @Autowired
    private lateinit var recordingFilter: ExceptionRecordingFilter

    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityChain: FilterChainProxy

    private lateinit var recordingMvc: MockMvc

    @BeforeEach
    fun clean() {
        records.deleteAll()
        recordingMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(recordingFilter, securityChain)
                .build()
    }

    @AfterEach
    fun resetClock() {
        clock.reset()
    }

    private fun failOnce(path: String) {
        val admin = createUserWithRole(Role.ADMIN)
        runCatching { recordingMvc.perform(get(path).with(signedIn(admin))) }
    }

    @Test
    fun `a request failing twice in one place is one fault, counted twice, under its route`() {
        failOnce("/test-only/fails/1")
        failOnce("/test-only/fails/2")

        val fault = records.findAll().single()
        assertThat(fault.occurrences).isEqualTo(2)
        assertThat(fault.exceptionType).isEqualTo("java.lang.IllegalStateException")
        assertThat(fault.thrownAt).isEqualTo("${Failing::class.java.name}.fails")
        assertThat(fault.latestConcern).isEqualTo("GET /test-only/fails/{id}")
        assertThat(fault.latestSource).isEqualTo(ExceptionSource.REQUEST)
        assertThat(fault.latestMessage).isEqualTo("broke 2")
        assertThat(fault.latestStackTrace).contains("broke 2")
    }

    @Test
    fun `the same type thrown from another place is another fault`() {
        failOnce("/test-only/fails/1")
        failOnce("/test-only/fails-elsewhere")

        assertThat(records.findAll().map { it.thrownAt })
            .containsExactlyInAnyOrder("${Failing::class.java.name}.fails", "${Failing::class.java.name}.failsElsewhere")
    }

    @Test
    fun `a request answered with a refusal is not a fault`() {
        val admin = createUserWithRole(Role.ADMIN)

        recordingMvc.perform(get("/management/jobs/987654321").with(signedIn(admin))).andExpect(status().isNotFound)

        assertThat(records.count()).isZero()
    }

    @Test
    fun `a resolved fault reopens when it fires again`() {
        val admin = createUserWithRole(Role.ADMIN)
        failOnce("/test-only/fails/1")
        val id = records.findAll().single().id!!

        mvc
            .perform(post("/management/exceptions/$id/resolve").with(signedIn(admin)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resolvedAt").isNotEmpty)
        assertThat(faults.list(resolved = true)).hasSize(1)
        assertThat(faults.list(resolved = false)).isEmpty()

        failOnce("/test-only/fails/3")

        val reopened = records.findById(id).orElseThrow()
        assertThat(reopened.resolvedAt).isNull()
        assertThat(reopened.occurrences).isEqualTo(2)
    }

    @Test
    fun `a job's fault names the job and the execution`() {
        recorder.record(IllegalArgumentException("bad payload"), ExceptionConcern(ExceptionSource.JOB, "contact.sync", 41L))

        val fault = records.findAll().single()
        assertThat(fault.latestSource).isEqualTo(ExceptionSource.JOB)
        assertThat(fault.latestConcern).isEqualTo("contact.sync")
        assertThat(fault.latestJobExecutionId).isEqualTo(41L)
    }

    @Test
    fun `faults quiet for longer than the retention period are forgotten`() {
        clock.set(clock.instant().minus(RecordedExceptions.RETENTION).minus(Duration.ofDays(1)))
        recorder.record(IllegalStateException("old"), ExceptionConcern(ExceptionSource.JOB, "old.job", null))
        clock.reset()
        recorder.record(UnsupportedOperationException("new"), ExceptionConcern(ExceptionSource.JOB, "new.job", null))

        assertThat(faults.purgeExpired()).isEqualTo(1)
        assertThat(records.findAll().map { it.latestConcern }).containsExactly("new.job")
    }

    @Nested
    inner class OnlyAdminsReadThem {
        @Test
        fun `an admin lists, opens and resolves faults`() {
            val admin = createUserWithRole(Role.ADMIN)
            recorder.record(IllegalStateException("seen"), ExceptionConcern(ExceptionSource.JOB, "contact.sync", null))
            val id = records.findAll().single().id!!

            mvc
                .perform(get("/management/exceptions").with(signedIn(admin)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].latestStackTrace").doesNotExist())
            mvc
                .perform(get("/management/exceptions/$id").with(signedIn(admin)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.latestStackTrace").isNotEmpty)
            mvc
                .perform(get("/management/exceptions/987654321").with(signedIn(admin)))
                .andExpect(status().isNotFound)
        }

        @Test
        fun `the board and members cannot read or resolve them`() {
            recorder.record(IllegalStateException("seen"), ExceptionConcern(ExceptionSource.JOB, "contact.sync", null))
            val id = records.findAll().single().id!!

            for (role in listOf(Role.BOARD, Role.MEMBER)) {
                val user = createUserWithRole(role)
                mvc.perform(get("/management/exceptions").with(signedIn(user))).andExpect(status().isForbidden)
                mvc.perform(get("/management/exceptions/$id").with(signedIn(user))).andExpect(status().isForbidden)
                mvc.perform(post("/management/exceptions/$id/resolve").with(signedIn(user))).andExpect(status().isForbidden)
            }
            mvc.perform(get("/management/exceptions")).andExpect(status().isUnauthorized)
        }
    }
}
