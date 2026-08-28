package net.blueshell.api.jobs.api

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import net.blueshell.api.platform.config.JobQueueProperties
import net.blueshell.api.shared.job.NonRetryableJobException
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.time.Instant
import kotlin.math.min
import kotlin.math.pow
import net.blueshell.api.jobs.domain.JobHandlerRegistry

/**
 * Runs job handlers and persists their outcome.
 *
 * Each invocation performs exactly one attempt. On a retryable failure the
 * execution is re-queued in the DB with a `next_attempt_at` timestamp computed
 * from an exponential backoff schedule; a separate scheduler ([StaleJobRecovery])
 * picks the row up once that timestamp is reached. This survives restarts and
 * spans hours without holding a worker thread.
 */
@Service
class JobExecutor(
    private val jobExecutionService: JobExecutionService,
    @param:Lazy private val jobHandlerRegistry: JobHandlerRegistry,
    private val properties: JobQueueProperties,
    private val meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(JobExecutor::class.java)

    @Async("externalApiExecutor")
    fun executeAsync(executionId: Long) {
        val execution = jobExecutionService.findByIdOrNull(executionId)
        if (execution == null) {
            logger.warn("Job execution {} not found; skipping.", executionId)
            return
        }
        execute(execution)
    }

    fun execute(execution: net.blueshell.api.jobs.persistence.JobExecution) {
        val handler = jobHandlerRegistry.get(execution.jobType)
        if (handler == null) {
            jobExecutionService.markDead(
                execution,
                errorType = "NoHandlerRegisteredException",
                errorReason = "No handler registered for job type ${execution.jobType}."
            )
            meterRegistry.counter("job.dead.count", "job_type", execution.jobType).increment()
            return
        }

        val current = jobExecutionService.markRunning(execution)
        val sample = Timer.start(meterRegistry)

        try {
            handler.handle(current.payload, current.id)
            jobExecutionService.markSuccess(current)
            sample.stop(meterRegistry.timer("job.execution.duration", "job_type", current.jobType, "outcome", "success"))
        } catch (ex: Exception) {
            handleFailure(current, ex, sample)
        }
    }

    private fun handleFailure(
        execution: net.blueshell.api.jobs.persistence.JobExecution,
        ex: Exception,
        sample: Timer.Sample
    ) {
        val errorType = ex::class.java.name
        val errorReason = ex.message ?: "Unknown error"
        val stackTrace = ex.stackTraceToString()

        if (isNonRetryable(ex)) {
            // Non-retryable means "retrying will not change the outcome", so
            // we stop attempts here. We use FAILED rather than DEAD: DEAD is
            // reserved for jobs the queue itself cannot run (e.g. no handler
            // registered), while FAILED surfaces an attempt-level error that
            // an operator can clear with the Retry button once the underlying
            // bug or input is fixed.
            logger.error(
                "Job execution {} failed with non-retryable error. errorType={}, errorReason={}.",
                execution.id, errorType, errorReason, ex
            )
            jobExecutionService.markFailed(execution, errorType, errorReason, stackTrace)
            sample.stop(meterRegistry.timer("job.execution.duration", "job_type", execution.jobType, "outcome", "failed"))
            meterRegistry.counter("job.failed.count", "job_type", execution.jobType).increment()
            return
        }

        // attempts is now the 1-indexed counter of the run that just
        // finished. With maxRetries == 3 we permit 4 total attempts, so we
        // give up once the just-failed run is the (maxRetries + 1)th one.
        if (execution.attempts >= properties.maxRetries + 1) {
            logger.error(
                "Job execution {} failed after {} attempts; giving up. errorType={}, errorReason={}.",
                execution.id, execution.attempts, errorType, errorReason, ex
            )
            jobExecutionService.markFailed(execution, errorType, errorReason, stackTrace)
            sample.stop(meterRegistry.timer("job.execution.duration", "job_type", execution.jobType, "outcome", "failed"))
            meterRegistry.counter("job.failed.count", "job_type", execution.jobType).increment()
            return
        }

        // Backoff schedule indexes from 0 = "first failure" so the initial
        // delay is exactly initialBackoffMillis. attempts is 1-indexed
        // (1 on the first failure), so subtract one before computing.
        val nextAttemptAt = Instant.now().plusMillis(computeBackoffMillis(execution.attempts - 1))
        logger.warn(
            "Job execution {} failed (attempt {}/{}). Scheduling retry at {}. errorType={}, errorReason={}.",
            execution.id, execution.attempts, properties.maxRetries + 1, nextAttemptAt, errorType, errorReason
        )
        jobExecutionService.markRetryScheduled(execution, errorType, errorReason, stackTrace, nextAttemptAt)
        sample.stop(
            meterRegistry.timer("job.execution.duration", "job_type", execution.jobType, "outcome", "retry-scheduled")
        )
        meterRegistry.counter("job.retry.scheduled.count", "job_type", execution.jobType).increment()
    }

    /**
     * Exponential backoff. [attemptsSoFar] is the number of completed
     * failures (0 means "this is the first failure, use the base delay").
     * Capped at [JobQueueProperties.maxBackoffMillis].
     */
    private fun computeBackoffMillis(attemptsSoFar: Int): Long {
        val raw = properties.initialBackoffMillis.toDouble() *
            properties.backoffMultiplier.pow(attemptsSoFar.toDouble())
        val capped = min(raw, properties.maxBackoffMillis.toDouble())
        return capped.toLong().coerceAtLeast(0L)
    }

    private fun isNonRetryable(ex: Exception): Boolean {
        return NonRetryableJobException.NON_RETRYABLE_EXCEPTIONS.any { it.isInstance(ex) }
    }
}
