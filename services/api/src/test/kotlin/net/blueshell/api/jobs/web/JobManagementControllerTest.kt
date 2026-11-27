package net.blueshell.api.jobs.web

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import net.blueshell.api.jobs.api.JobExecutionService
import net.blueshell.api.jobs.domain.JobDispatcher
import net.blueshell.api.jobs.persistence.JobExecution
import net.blueshell.api.shared.enums.JobExecutionStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.server.ResponseStatusException

class JobManagementControllerTest {
    private val executions: JobExecutionService = mock()
    private val dispatcher: JobDispatcher = mock()
    private val views: JobExecutionViewService = mock()
    private val controller = JobManagementController(executions, dispatcher, views, mock(), SimpleMeterRegistry())

    private fun execution(status: JobExecutionStatus) = JobExecution(jobType = "demo", status = status).apply { id = 7L }

    @Test
    fun `runs a skipped job again, as it does a failed one`() {
        val skipped = execution(JobExecutionStatus.SKIPPED)
        whenever(executions.findById(7L)).thenReturn(skipped)
        whenever(executions.retryWithSupersede(skipped)).thenReturn(skipped)

        controller.retry(7L)

        verify(dispatcher).dispatch(skipped)
    }

    @Test
    fun `an email retry goes through the dispatcher that routes it to the batch worker`() {
        val email = JobExecution(jobType = "email.signup", status = JobExecutionStatus.FAILED).apply { id = 9L }
        whenever(executions.findById(9L)).thenReturn(email)
        whenever(executions.retryWithSupersede(email)).thenReturn(email)

        controller.retry(9L)

        verify(dispatcher).dispatch(email)
    }

    @Test
    fun `refuses to run again a job that succeeded`() {
        whenever(executions.findById(7L)).thenReturn(execution(JobExecutionStatus.SUCCESS))

        assertThatThrownBy { controller.retry(7L) }
            .isInstanceOf(ResponseStatusException::class.java)
            .hasMessageContaining("Only FAILED, DEAD or SKIPPED jobs can be retried")
    }

    @Test
    fun `counts skipped runs apart from successes`() {
        whenever(executions.countAllByStatus()).thenReturn(
            mapOf(JobExecutionStatus.SUCCESS to 3L, JobExecutionStatus.SKIPPED to 2L),
        )

        val stats = controller.getStats()

        assertThat(stats.skippedCount).isEqualTo(2L)
        assertThat(stats.successCount).isEqualTo(3L)
        assertThat(stats.totalCount).isEqualTo(5L)
    }
}
