package net.blueshell.api.jobs.domain

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.blueshell.api.jobs.api.JobExecutionService
import net.blueshell.api.jobs.api.JobExecutor
import net.blueshell.api.jobs.persistence.JobExecution
import net.blueshell.api.platform.config.JobQueueProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.springframework.core.task.TaskRejectedException
import java.time.Duration
import java.time.Instant

class StaleJobRecoveryTest {
    private val executions: JobExecutionService = mockk()
    private val executor: JobExecutor = mockk()
    private val properties: JobQueueProperties =
        mockk<JobQueueProperties> {
            every { staleThresholdMinutes } returns 30
            every { undispatchedGraceSeconds } returns 120
            every { staleRecoveryBatchSize } returns 5
        }
    private val worker: EmailJobWorker = mockk(relaxUnitFun = true)
    private val meters = SimpleMeterRegistry()
    private val recovery = StaleJobRecovery(executions, executor, worker, properties, meters)

    @Test
    fun `a rejected recovery dispatch stops the loop without throwing`() {
        val first = execution(7)
        val second = execution(8)
        every { executions.findStaleRunning(any(), any()) } returns listOf(first, second)
        every { executions.findStaleQueued(any(), any()) } returns emptyList()
        every { executions.resetRunningToQueued(any()) } returns true
        every { executor.executeAsync(7L) } throws TaskRejectedException("Executor full")

        assertThatCode { recovery.recoverStaleJobs() }.doesNotThrowAnyException()

        verify(exactly = 1) { executions.resetRunningToQueued(first) }
        verify(exactly = 0) { executor.executeAsync(8L) }
    }

    @Test
    fun `a stale row another runner reset is neither dispatched nor counted`() {
        val first = execution(31)
        val second = execution(32)
        every { executions.findStaleRunning(any(), any()) } returns listOf(first, second)
        every { executions.findStaleQueued(any(), any()) } returns emptyList()
        every { executions.resetRunningToQueued(first) } returns false
        every { executions.resetRunningToQueued(second) } returns true
        every { executor.executeAsync(any()) } returns Unit

        recovery.recoverStaleJobs()

        verify(exactly = 1) { executions.resetRunningToQueued(first) }
        verify(exactly = 0) { executor.executeAsync(31L) }
        verify { executor.executeAsync(32L) }
        assertThat(meters.find("job.recovery.count").counter()?.count()).isEqualTo(1.0)
    }

    @Test
    fun `a rejected retry dispatch stops the loop without throwing`() {
        every { executions.findDueScheduledRetries(any(), any()) } returns listOf(execution(11), execution(12))
        every { executor.executeAsync(11L) } throws TaskRejectedException("Executor full")

        assertThatCode { recovery.dispatchDueRetries() }.doesNotThrowAnyException()

        verify(exactly = 0) { executor.executeAsync(12L) }
    }

    @Test
    fun `undispatched queued rows are swept after the grace period, not the stale threshold`() {
        val threshold = slot<Instant>()
        every { executions.findStaleRunning(any(), any()) } returns emptyList()
        every { executions.findStaleQueued(capture(threshold), any()) } returns emptyList()

        recovery.recoverStaleJobs()

        assertThat(Duration.between(threshold.captured, Instant.now()).seconds).isBetween(115L, 125L)
    }

    @Test
    fun `due rows are dispatched and counted`() {
        every { executions.findStaleRunning(any(), any()) } returns emptyList()
        every { executions.findStaleQueued(any(), any()) } returns listOf(execution(21))
        every { executions.findDueScheduledRetries(any(), any()) } returns listOf(execution(22))
        every { executor.executeAsync(any()) } returns Unit

        recovery.recoverStaleJobs()
        recovery.dispatchDueRetries()

        verify { executor.executeAsync(21L) }
        verify { executor.executeAsync(22L) }
    }

    @Test
    fun `email retries are left to the batch worker`() {
        every { executions.findStaleRunning(any(), any()) } returns emptyList()
        every { executions.findStaleQueued(any(), any()) } returns emptyList()
        val email = JobExecution(jobType = "email.signup").apply { id = 21 }
        every { executions.findDueScheduledRetries(any(), any()) } returns listOf(email)

        recovery.dispatchDueRetries()

        verify(exactly = 0) { executor.executeAsync(any()) }
        verify(exactly = 0) { worker.nudge() }
    }

    @Test
    fun `a stale running email job is requeued and the worker is nudged`() {
        val email = JobExecution(jobType = "email.signup").apply { id = 22 }
        every { executions.findStaleRunning(any(), any()) } returns listOf(email)
        every { executions.findStaleQueued(any(), any()) } returns emptyList()
        every { executions.resetRunningToQueued(any()) } returns true

        recovery.recoverStaleJobs()

        verify { executions.resetRunningToQueued(email) }
        verify { worker.nudge() }
        verify(exactly = 0) { executor.executeAsync(any()) }
    }

    private fun execution(id: Long) = JobExecution(jobType = "test.job").apply { this.id = id }
}
