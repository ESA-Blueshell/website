package net.blueshell.api.jobs.domain

import io.micrometer.core.instrument.MeterRegistry
import net.blueshell.api.jobs.api.JobExecutionService
import net.blueshell.api.jobs.api.JobExecutor
import net.blueshell.api.platform.config.JobQueueProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.task.TaskRejectedException
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Recovers RUNNING rows past the stale threshold and undispatched QUEUED rows past a short grace,
 * then fires retries whose `next_attempt_at` has elapsed; a QUEUED row behind a RUNNING twin waits.
 */
@Component
@ConditionalOnProperty(name = ["app.jobs.recovery.enabled"], havingValue = "true", matchIfMissing = true)
class StaleJobRecovery(
    private val jobExecutionService: JobExecutionService,
    private val jobExecutor: JobExecutor,
    private val properties: JobQueueProperties,
    private val meterRegistry: MeterRegistry,
) {
    private val logger = LoggerFactory.getLogger(StaleJobRecovery::class.java)

    @Scheduled(fixedDelayString = "\${app.jobs.stale-check-interval-ms:60000}")
    fun recoverStaleJobs() {
        val now = Instant.now()
        val runningThreshold = now.minus(properties.staleThresholdMinutes, ChronoUnit.MINUTES)
        val queuedThreshold = now.minus(properties.undispatchedGraceSeconds, ChronoUnit.SECONDS)
        val pageable = PageRequest.of(0, properties.staleRecoveryBatchSize)

        val staleRunning = jobExecutionService.findStaleRunning(runningThreshold, pageable)
        for (execution in staleRunning) {
            logger.warn(
                "Recovering stale RUNNING job execution {}. jobType={}, startedAt={}",
                execution.id,
                execution.jobType,
                execution.startedAt,
            )
            jobExecutionService.resetRunningToQueued(execution)
            if (!dispatch(execution.id!!)) break
        }

        val staleQueued = jobExecutionService.findStaleQueued(queuedThreshold, pageable)
        for (execution in staleQueued) {
            logger.warn(
                "Recovering stale QUEUED job execution {}. jobType={}, queuedAt={}",
                execution.id,
                execution.jobType,
                execution.queuedAt,
            )
            if (!dispatch(execution.id!!)) break
        }

        val totalRecovered = staleRunning.size + staleQueued.size
        if (totalRecovered > 0) {
            meterRegistry.counter("job.recovery.count").increment(totalRecovered.toDouble())
            logger.info("Recovered {} stale jobs.", totalRecovered)
        }
    }

    @Scheduled(fixedDelayString = "\${app.jobs.retry-check-interval-ms:30000}")
    fun dispatchDueRetries() {
        val now = Instant.now()
        val pageable = PageRequest.of(0, properties.staleRecoveryBatchSize)
        val due = jobExecutionService.findDueScheduledRetries(now, pageable)
        for (execution in due) {
            logger.info(
                "Dispatching scheduled retry for job execution {}. jobType={}, attempts={}, nextAttemptAt={}",
                execution.id,
                execution.jobType,
                execution.attempts,
                execution.nextAttemptAt,
            )
            if (!dispatch(execution.id!!)) break
        }
        if (due.isNotEmpty()) {
            meterRegistry.counter("job.retry.dispatched.count").increment(due.size.toDouble())
        }
    }

    private fun dispatch(executionId: Long): Boolean =
        try {
            jobExecutor.executeAsync(executionId)
            true
        } catch (ex: TaskRejectedException) {
            logger.warn("Job execution {} could not be dispatched; leaving later rows for the next tick.", executionId, ex)
            false
        }
}
