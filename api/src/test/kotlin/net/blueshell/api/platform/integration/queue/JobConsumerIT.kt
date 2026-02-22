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

@Import(JobConsumerITConfig::class)
class JobConsumerIT : ServiceTestSupport() {

    @Autowired
    private lateinit var dispatcher: JobDispatcher

    @Autowired
    private lateinit var consumer: JobConsumer

    @Autowired
    private lateinit var retryingHandler: RetryingTestJobHandler

    @Autowired
    private lateinit var jobQueueProperties: JobQueueProperties

    @BeforeEach
    fun resetHandler() {
        retryingHandler.reset()
    }

    @Test
    fun `retries with exponential backoff and eventually succeeds`() {
        retryingHandler.failForFirstCalls(2)
        val execution = dispatcher.enqueue(RetryingTestJobHandler.JOB_TYPE, mapOf("id" to "123"))

        consumer.handle(JobMessage(execution.id!!, execution.jobType, execution.payload))

        val updated = jobExecutions.findById(execution.id!!).orElseThrow()
        assertThat(updated.status).isEqualTo(JobExecutionStatus.SUCCESS)
        assertThat(updated.attempts).isEqualTo(2)
        assertThat(updated.errorType).isNull()
        assertThat(updated.errorReason).isNull()
        assertThat(retryingHandler.invocations()).isEqualTo(3)
    }

    @Test
    fun `fails after exhausting configured retries and stores error details`() {
        retryingHandler.alwaysFail()
        val execution = dispatcher.enqueue(RetryingTestJobHandler.JOB_TYPE, mapOf("id" to "456"))

        consumer.handle(JobMessage(execution.id!!, execution.jobType, execution.payload))

        val updated = jobExecutions.findById(execution.id!!).orElseThrow()
        assertThat(updated.status).isEqualTo(JobExecutionStatus.FAILED)
        assertThat(updated.attempts).isEqualTo(jobQueueProperties.maxRetries)
        assertThat(updated.errorType).isEqualTo(IllegalStateException::class.java.name)
        assertThat(updated.errorReason).contains("planned failure")
        assertThat(updated.errorReason).contains("RetryingTestJobHandler.handle")
        assertThat(updated.errorMessage).contains("planned failure")
        assertThat(retryingHandler.invocations()).isEqualTo(jobQueueProperties.maxRetries + 1)
    }

    @Test
    fun `marks missing handler errors with type and reason`() {
        val execution = dispatcher.enqueue("test.missing.handler", mapOf("id" to "789"))

        consumer.handle(JobMessage(execution.id!!, execution.jobType, execution.payload))

        val updated = jobExecutions.findById(execution.id!!).orElseThrow()
        assertThat(updated.status).isEqualTo(JobExecutionStatus.FAILED)
        assertThat(updated.errorType).isEqualTo("NoHandlerRegisteredException")
        assertThat(updated.errorReason).contains("No handler registered for job type test.missing.handler.")
        assertThat(updated.attempts).isEqualTo(0)
    }
}

@TestConfiguration
class JobConsumerITConfig {
    @Bean
    fun retryingTestJobHandler(): RetryingTestJobHandler = RetryingTestJobHandler()
}

class RetryingTestJobHandler : JobHandler {
    override val jobType: String = JOB_TYPE

    private val invocationCounter = AtomicInteger(0)
    @Volatile
    private var failuresBeforeSuccess: Int = 0

    override fun handle(payload: String?) {
        val currentInvocation = invocationCounter.incrementAndGet()
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

    fun reset() {
        invocationCounter.set(0)
        failuresBeforeSuccess = 0
    }

    fun invocations(): Int = invocationCounter.get()

    companion object {
        const val JOB_TYPE = "test.retry.job"
    }
}
