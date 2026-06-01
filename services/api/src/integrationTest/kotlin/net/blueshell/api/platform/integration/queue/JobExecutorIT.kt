package net.blueshell.api.platform.integration.queue

import net.blueshell.api.platform.config.JobQueueProperties
import net.blueshell.api.shared.enums.JobExecutionStatus
import net.blueshell.api.testsupport.ServiceTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import java.util.concurrent.atomic.AtomicInteger

@Import(JobExecutorITConfig::class)
// These tests drive the executor manually. The test profile runs
// StaleJobRecovery's retry/stale schedulers every 100ms, which would pick up
// the same QUEUED job and call markRetryScheduled concurrently with the manual
// execute() loop — an optimistic-lock race ("expected row count 1 but was 0").
// Park both schedulers so manual execution is the only writer.
@TestPropertySource(
    properties = [
        "app.jobs.retry-check-interval-ms=3600000",
        "app.jobs.stale-check-interval-ms=3600000",
    ],
)
class JobExecutorIT : ServiceTestSupport() {

    @Autowired
    private lateinit var dispatcher: JobDispatcher

    @Autowired
    private lateinit var executor: JobExecutor

    @Autowired
    private lateinit var retryingHandler: RetryingTestJobHandler

    @Autowired
    private lateinit var jobQueueProperties: JobQueueProperties

    @BeforeEach
    fun resetHandler() {
        retryingHandler.reset()
    }

    @Test
    fun `first failure schedules a retry and bumps attempts to 2`() {
        retryingHandler.failForFirstCalls(1)
        // Enqueue starts attempts at 1 (the initial run is already counted).
        val execution = dispatcher.enqueue(RetryingTestJobHandler.JOB_TYPE, mapOf("id" to "123"))!!
        assertThat(execution.attempts).describedAs("initial enqueue counts as attempt 1").isEqualTo(1)

        executor.execute(jobExecutions.findById(execution.id!!).orElseThrow())

        val updated = jobExecutions.findById(execution.id!!).orElseThrow()
        assertThat(updated.status).isEqualTo(JobExecutionStatus.QUEUED)
        // First failure scheduled a retry → the upcoming attempt is the 2nd.
        assertThat(updated.attempts).isEqualTo(2)
        assertThat(updated.nextAttemptAt).isNotNull()
        assertThat(updated.errorType).isEqualTo(IllegalStateException::class.java.name)
        assertThat(retryingHandler.invocations()).isEqualTo(1)
    }

    @Test
    fun `repeated executions eventually succeed`() {
        retryingHandler.failForFirstCalls(2)
        val execution = dispatcher.enqueue(RetryingTestJobHandler.JOB_TYPE, mapOf("id" to "abc"))!!

        repeat(3) {
            executor.execute(jobExecutions.findById(execution.id!!).orElseThrow())
        }

        val updated = jobExecutions.findById(execution.id!!).orElseThrow()
        assertThat(updated.status).isEqualTo(JobExecutionStatus.SUCCESS)
        assertThat(retryingHandler.invocations()).isEqualTo(3)
    }

    @Test
    fun `exhausting maxRetries marks the job as FAILED`() {
        retryingHandler.alwaysFail()
        val execution = dispatcher.enqueue(RetryingTestJobHandler.JOB_TYPE, mapOf("id" to "456"))!!

        val maxInvocations = jobQueueProperties.maxRetries + 1
        repeat(maxInvocations) {
            executor.execute(jobExecutions.findById(execution.id!!).orElseThrow())
        }

        val updated = jobExecutions.findById(execution.id!!).orElseThrow()
        assertThat(updated.status).isEqualTo(JobExecutionStatus.FAILED)
        assertThat(updated.errorType).isEqualTo(IllegalStateException::class.java.name)
        assertThat(updated.errorReason).contains("planned failure")
        assertThat(updated.errorMessage).contains("planned failure")
        assertThat(retryingHandler.invocations()).isEqualTo(maxInvocations)
    }

    @Test
    fun `marks missing handler errors as DEAD`() {
        val execution = dispatcher.enqueue("test.missing.handler", mapOf("id" to "789"))!!

        executor.execute(jobExecutions.findById(execution.id!!).orElseThrow())

        val updated = jobExecutions.findById(execution.id!!).orElseThrow()
        assertThat(updated.status).isEqualTo(JobExecutionStatus.DEAD)
        assertThat(updated.errorType).isEqualTo("NoHandlerRegisteredException")
        assertThat(updated.errorReason).contains("No handler registered for job type test.missing.handler.")
        // Initial enqueue counts as attempt 1; markDead leaves it untouched.
        assertThat(updated.attempts).isEqualTo(1)
    }

    @Test
    fun `non-retryable exception marks job as DEAD immediately`() {
        retryingHandler.throwNonRetryable()
        val execution = dispatcher.enqueue(RetryingTestJobHandler.JOB_TYPE, mapOf("id" to "dead"))!!

        executor.execute(jobExecutions.findById(execution.id!!).orElseThrow())

        val updated = jobExecutions.findById(execution.id!!).orElseThrow()
        assertThat(updated.status).isEqualTo(JobExecutionStatus.DEAD)
        assertThat(retryingHandler.invocations()).isEqualTo(1)
    }
}

@TestConfiguration
class JobExecutorITConfig {
    @Bean
    fun retryingTestJobHandler(): RetryingTestJobHandler = RetryingTestJobHandler()
}

class RetryingTestJobHandler : JobHandler {
    override val jobType: String = JOB_TYPE
    override val payloadType: Class<*> = Map::class.java

    private val invocationCounter = AtomicInteger(0)
    @Volatile
    private var failuresBeforeSuccess: Int = 0
    @Volatile
    private var throwNonRetryable: Boolean = false

    override fun handle(payload: String?, executionId: Long?) {
        val currentInvocation = invocationCounter.incrementAndGet()
        if (throwNonRetryable) {
            throw IllegalArgumentException("non-retryable failure")
        }
        if (currentInvocation <= failuresBeforeSuccess) {
            throw IllegalStateException("planned failure $currentInvocation")
        }
    }

    fun failForFirstCalls(count: Int) {
        failuresBeforeSuccess = count
    }

    fun alwaysFail() {
        failuresBeforeSuccess = Int.MAX_VALUE
    }

    fun throwNonRetryable() {
        throwNonRetryable = true
    }

    fun reset() {
        invocationCounter.set(0)
        failuresBeforeSuccess = 0
        throwNonRetryable = false
    }

    fun invocations(): Int = invocationCounter.get()

    companion object {
        const val JOB_TYPE = "test.retry.job"
    }
}
