package net.blueshell.api.platform.integration.queue

import io.micrometer.core.instrument.MeterRegistry
import net.blueshell.api.platform.config.JobQueueProperties
import net.blueshell.api.platform.integration.job.application.service.JobExecutionService
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Recovers jobs orphaned by app crashes.
 * Periodically checks for stale RUNNING or QUEUED jobs and re-executes them.
 */
@Component
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
}
