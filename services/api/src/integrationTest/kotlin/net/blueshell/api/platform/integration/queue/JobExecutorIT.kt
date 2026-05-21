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
import java.util.concurrent.atomic.AtomicInteger

@Import(JobExecutorITConfig::class)
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
    fun `first failure schedules a retry and increments attempts`() {
        retryingHandler.failForFirstCalls(1)
        val execution = dispatcher.enqueue(RetryingTestJobHandler.JOB_TYPE, mapOf("id" to "123"))!!

        executor.execute(jobExecutions.findById(execution.id!!).orElseThrow())

        val updated = jobExecutions.findById(execution.id!!).orElseThrow()
        assertThat(updated.status).isEqualTo(JobExecutionStatus.QUEUED)
        assertThat(updated.attempts).isEqualTo(1)
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
        assertThat(updated.attempts).isEqualTo(0)
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
