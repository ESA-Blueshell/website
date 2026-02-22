package net.blueshell.api.platform.integration.queue

import net.blueshell.api.platform.config.JobQueueProperties
import net.blueshell.api.platform.integration.job.persistence.JobExecution
import net.blueshell.api.platform.integration.job.service.JobExecutionService
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component
import kotlin.math.pow

@Component
class JobConsumer(
    private val jobExecutionService: JobExecutionService,
    private val jobHandlerRegistry: JobHandlerRegistry,
    private val jobQueueProperties: JobQueueProperties
) {
    private val logger = LoggerFactory.getLogger(JobConsumer::class.java)

    @RabbitListener(queues = ["\${app.jobs.queue-name}"])
    fun handle(message: JobMessage) {
        val execution = jobExecutionService.findByIdOrNull(message.executionId)
        if (execution == null) {
            logger.warn("Job execution {} not found; skipping message.", message.executionId)
            return
        }

        val handler = jobHandlerRegistry.get(message.jobType)
        if (handler == null) {
            jobExecutionService.markFailed(
                execution,
                errorType = "NoHandlerRegisteredException",
                errorReason = "No handler registered for job type ${message.jobType}."
            )
            return
        }

        runJob(execution, handler, message.payload)
    }

    private fun runJob(execution: JobExecution, handler: JobHandler, payload: String?) {
        var current = execution
        val maxRetries = jobQueueProperties.maxRetries.coerceAtLeast(0)

        while (true) {
            current = jobExecutionService.markRunning(current)
            try {
                handler.handle(payload)
                jobExecutionService.markSuccess(current)
                return
            } catch (ex: Exception) {
                val errorType = ex::class.java.name
                val errorReason = ex.message ?: "Unknown error"
                val stackTrace = ex.stackTraceToString()
                if (current.attempts >= maxRetries) {
                    logger.error(
                        "Job execution {} failed after {} retries. errorType={}, errorReason={}.",
                        current.id,
                        current.attempts,
                        errorType,
                        errorReason,
                        ex
                    )
                    jobExecutionService.markFailed(
                        current,
                        errorType,
                        errorReason,
                        stackTrace = stackTrace
                    )
                    return
                }

                val retryNumber = current.attempts + 1
                val backoffMillis = calculateBackoffMillis(retryNumber)
                logger.warn(
                    "Job execution {} failed; retrying in {} ms (retry {}/{}). errorType={}, errorReason={}.",
                    current.id,
                    backoffMillis,
                    retryNumber,
                    maxRetries,
                    errorType,
                    errorReason,
                    ex
                )
                current = jobExecutionService.markRetryQueued(
                    current,
                    errorType,
                    errorReason,
                    stackTrace = stackTrace
                )
                sleep(backoffMillis)
            }
        }
    }

    private fun calculateBackoffMillis(retryNumber: Int): Long {
        val initialDelay = jobQueueProperties.initialBackoffMillis.coerceAtLeast(0)
        val multiplier = jobQueueProperties.backoffMultiplier.coerceAtLeast(1.0)
        val exponent = (retryNumber - 1).coerceAtLeast(0)
        val delay = initialDelay.toDouble() * multiplier.pow(exponent.toDouble())
        return delay.toLong().coerceAtLeast(0)
    }

    private fun sleep(delayMillis: Long) {
        if (delayMillis <= 0L) return
        try {
            Thread.sleep(delayMillis)
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            logger.warn("Interrupted while waiting to retry job execution.")
        }
    }
}
