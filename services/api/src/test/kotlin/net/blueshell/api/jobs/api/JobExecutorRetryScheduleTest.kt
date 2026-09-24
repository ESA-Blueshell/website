package net.blueshell.api.jobs.api

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import net.blueshell.api.jobs.domain.JobHandler
import net.blueshell.api.jobs.domain.JobHandlerRegistry
import net.blueshell.api.jobs.persistence.JobExecution
import net.blueshell.api.platform.config.JobQueueProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.Instant

class JobExecutorRetryScheduleTest {
    private val executions: JobExecutionService = mock()
    private val properties = JobQueueProperties(maxRetries = 3, initialBackoffMillis = 1_000)

    /** A handler that always fails, on its own schedule or the queue's. */
    private class Failing(
        override val retrySchedule: RetrySchedule?,
    ) : JobHandler {
        override val jobType = "failing"
        override val payloadType = String::class.java

        override fun handle(
            payload: String?,
            executionId: Long?,
            forced: Boolean,
        ): JobOutcome = error("down")
    }

    private fun run(
        handler: JobHandler,
        attempts: Int,
    ) {
        val execution = JobExecution(jobType = "failing", payload = "{}").apply { this.attempts = attempts }
        whenever(executions.markRunning(any())).thenReturn(execution)
        JobExecutor(executions, JobHandlerRegistry(listOf(handler)), properties, SimpleMeterRegistry()).execute(execution)
    }

    private val hours = RetrySchedule(10, Duration.ofMinutes(2), 2.0, Duration.ofHours(2))

    @Test
    fun `retries on the handler's own schedule, well past where the queue's would stop`() {
        val before = Instant.now()
        run(Failing(hours), attempts = 5)

        val next = argumentCaptor<Instant>()
        verify(executions).markRetryScheduled(any(), any(), any(), any(), next.capture())
        verify(executions, never()).markFailed(any(), any(), any(), any())
        // Two minutes doubled four times is 32 minutes.
        assertThat(Duration.between(before, next.firstValue)).isBetween(Duration.ofMinutes(31), Duration.ofMinutes(33))
    }

    @Test
    fun `caps the wait at the handler's own cap, and gives up after its own count`() {
        val before = Instant.now()
        run(Failing(hours), attempts = 10)
        val next = argumentCaptor<Instant>()
        verify(executions).markRetryScheduled(any(), any(), any(), any(), next.capture())
        assertThat(Duration.between(before, next.firstValue)).isBetween(Duration.ofMinutes(119), Duration.ofMinutes(121))

        run(Failing(hours), attempts = 11)
        verify(executions).markFailed(any(), eq("java.lang.IllegalStateException"), eq("down"), any())
    }

    @Test
    fun `keeps to the queue's schedule for a handler without one`() {
        run(Failing(null), attempts = 4)

        verify(executions).markFailed(any(), any(), any(), any())
    }

    @Test
    fun `a skipped run is recorded as skipped with its reason, and a forced one is told so`() {
        var told: Boolean? = null
        val skipping =
            object : JobHandler {
                override val jobType = "failing"
                override val payloadType = String::class.java

                override fun handle(
                    payload: String?,
                    executionId: Long?,
                    forced: Boolean,
                ): JobOutcome {
                    told = forced
                    return JobOutcome.Skipped("Not due yet.")
                }
            }
        val execution = JobExecution(jobType = "failing", payload = "{}", forced = true)
        whenever(executions.markRunning(any())).thenReturn(execution)

        JobExecutor(executions, JobHandlerRegistry(listOf(skipping)), properties, SimpleMeterRegistry()).execute(execution)

        verify(executions).markSkipped(execution, "Not due yet.")
        verify(executions, never()).markSuccess(any())
        assertThat(told).isTrue()
    }

    @Test
    fun `a run that did its work is recorded as a success`() {
        val done =
            object : JobHandler {
                override val jobType = "failing"
                override val payloadType = String::class.java

                override fun handle(
                    payload: String?,
                    executionId: Long?,
                    forced: Boolean,
                ) = JobOutcome.Done
            }
        val execution = JobExecution(jobType = "failing", payload = "{}")
        whenever(executions.markRunning(any())).thenReturn(execution)

        JobExecutor(executions, JobHandlerRegistry(listOf(done)), properties, SimpleMeterRegistry()).execute(execution)

        verify(executions).markSuccess(execution)
        verify(executions, never()).markSkipped(any(), any())
    }

    @Test
    fun `leaves a handler on the queue's schedule unless it says otherwise`() {
        val plain =
            object : JobHandler {
                override val jobType = "plain"
                override val payloadType = String::class.java

                override fun handle(
                    payload: String?,
                    executionId: Long?,
                    forced: Boolean,
                ) = JobOutcome.Done
            }

        assertThat(plain.retrySchedule).isNull()
    }
}

