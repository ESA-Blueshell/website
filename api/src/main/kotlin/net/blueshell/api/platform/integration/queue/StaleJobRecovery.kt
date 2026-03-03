package net.blueshell.api.platform.integration.queue

import io.micrometer.core.instrument.MeterRegistry
import net.blueshell.api.platform.config.JobQueueProperties
import net.blueshell.api.platform.integration.job.repository.JobExecutionRepository
import net.blueshell.api.shared.enums.JobExecutionStatus
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
    private val jobExecutionRepository: JobExecutionRepository,
    private val jobExecutor: JobExecutor,
    private val properties: JobQueueProperties,
    private val meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(StaleJobRecovery::class.java)

    @Scheduled(fixedDelayString = "\${app.jobs.stale-check-interval-ms:60000}")
    fun recoverStaleJobs() {
        val threshold = Instant.now().minus(properties.staleThresholdMinutes, ChronoUnit.MINUTES)
        val pageable = PageRequest.of(0, properties.staleRecoveryBatchSize)

        val staleRunning = jobExecutionRepository.findByStatusAndStartedAtBefore(
            JobExecutionStatus.RUNNING, threshold, pageable
        )
        for (execution in staleRunning) {
            logger.warn(
                "Recovering stale RUNNING job execution {}. jobType={}, startedAt={}",
                execution.id, execution.jobType, execution.startedAt
            )
            execution.status = JobExecutionStatus.QUEUED
            execution.startedAt = null
            execution.queuedAt = Instant.now()
            jobExecutionRepository.save(execution)
            jobExecutor.executeAsync(execution.id!!)
        }

        val staleQueued = jobExecutionRepository.findByStatusAndQueuedAtBefore(
            JobExecutionStatus.QUEUED, threshold, pageable
        )
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
