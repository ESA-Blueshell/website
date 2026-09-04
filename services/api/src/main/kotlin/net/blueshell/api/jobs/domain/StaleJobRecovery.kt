package net.blueshell.api.jobs.domain

import io.micrometer.core.instrument.MeterRegistry
import net.blueshell.api.platform.config.JobQueueProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit
import net.blueshell.api.jobs.api.JobExecutionService
import net.blueshell.api.jobs.api.JobExecutor

/**
 * Two scheduled sweeps: one recovers jobs orphaned by a crash — RUNNING rows whose handler
 * never finished, QUEUED rows the async dispatch missed — and one fires scheduled retries whose
 * `next_attempt_at` has elapsed, on a shorter interval so backoff windows do not slip.
 *
 * A crash-orphaned QUEUED row has a null `next_attempt_at` and a scheduled retry has it set, so
 * the two queries are disjoint and no row fires twice in a tick.
 */
// Default on. Tests that drive the executor manually disable it
// (app.jobs.recovery.enabled=false) so the scheduler does not race them.
@Component
@ConditionalOnProperty(name = ["app.jobs.recovery.enabled"], havingValue = "true", matchIfMissing = true)
class StaleJobRecovery(
    private val jobExecutionService: JobExecutionService,
    private val jobExecutor: JobExecutor,
    private val properties: JobQueueProperties,
    private val meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(StaleJobRecovery::class.java)

    @Scheduled(fixedDelayString = "\${app.jobs.stale-check-interval-ms:60000}")
    fun recoverStaleJobs() {
        val threshold = Instant.now().minus(properties.staleThresholdMinutes, ChronoUnit.MINUTES)
        val pageable = PageRequest.of(0, properties.staleRecoveryBatchSize)

        val staleRunning = jobExecutionService.findStaleRunning(threshold, pageable)
        for (execution in staleRunning) {
            logger.warn(
                "Recovering stale RUNNING job execution {}. jobType={}, startedAt={}",
                execution.id, execution.jobType, execution.startedAt
            )
            jobExecutionService.resetRunningToQueued(execution)
            jobExecutor.executeAsync(execution.id!!)
        }

        val staleQueued = jobExecutionService.findStaleQueued(threshold, pageable)
        for (execution in staleQueued) {
            logger.warn(
                "Recovering stale QUEUED job execution {}. jobType={}, queuedAt={}",
                execution.id, execution.jobType, execution.queuedAt
            )
            jobExecutor.executeAsync(execution.id!!)
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
                execution.id, execution.jobType, execution.attempts, execution.nextAttemptAt
            )
            jobExecutor.executeAsync(execution.id!!)
        }
        if (due.isNotEmpty()) {
            meterRegistry.counter("job.retry.dispatched.count").increment(due.size.toDouble())
        }
    }
}
