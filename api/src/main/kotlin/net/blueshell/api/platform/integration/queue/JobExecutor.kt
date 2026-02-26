package net.blueshell.api.platform.integration.queue

import net.blueshell.api.platform.integration.job.service.JobExecutionService
import net.blueshell.api.shared.job.NonRetryableJobException
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.retry.support.RetryTemplate
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class JobExecutor(
    private val jobExecutionService: JobExecutionService,
    @param:Lazy private val jobHandlerRegistry: JobHandlerRegistry,
    private val jobRetryTemplate: RetryTemplate
) {
    private val logger = LoggerFactory.getLogger(JobExecutor::class.java)

    @Async("taskExecutor")
    fun executeAsync(executionId: Long) {
        val execution = jobExecutionService.findByIdOrNull(executionId)
        if (execution == null) {
            logger.warn("Job execution {} not found; skipping.", executionId)
            return
        }
        execute(execution)
    }

    fun execute(execution: net.blueshell.api.platform.integration.job.persistence.JobExecution) {
        val handler = jobHandlerRegistry.get(execution.jobType)
        if (handler == null) {
            jobExecutionService.markDead(
                execution,
                errorType = "NoHandlerRegisteredException",
                errorReason = "No handler registered for job type ${execution.jobType}."
            )
            return
        }

        val current = jobExecutionService.markRunning(execution)

        try {
            jobRetryTemplate.execute<Unit, Exception> { context ->
                current.attempts = context.retryCount
                handler.handle(current.payload)
            }
            jobExecutionService.markSuccess(current)
        } catch (ex: Exception) {
            val errorType = ex::class.java.name
            val errorReason = ex.message ?: "Unknown error"
            val stackTrace = ex.stackTraceToString()

            if (isNonRetryable(ex)) {
                logger.error(
                    "Job execution {} failed with non-retryable error. errorType={}, errorReason={}.",
                    current.id, errorType, errorReason, ex
                )
                jobExecutionService.markDead(current, errorType, errorReason, stackTrace)
            } else {
                logger.error(
                    "Job execution {} failed after retries exhausted. errorType={}, errorReason={}.",
                    current.id, errorType, errorReason, ex
                )
                jobExecutionService.markFailed(current, errorType, errorReason, stackTrace)
            }
        }
    }

    private fun isNonRetryable(ex: Exception): Boolean {
        return ex is NonRetryableJobException ||
            ex is IllegalArgumentException ||
            ex is NullPointerException ||
            ex is ClassCastException
    }
}
