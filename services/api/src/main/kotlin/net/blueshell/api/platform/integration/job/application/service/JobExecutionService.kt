package net.blueshell.api.platform.integration.job.application.service

import net.blueshell.api.platform.integration.job.application.query.JobExecutionQuery
import net.blueshell.api.platform.integration.job.persistence.JobExecution
import net.blueshell.api.platform.integration.job.persistence.repository.JobExecutionRepository
import net.blueshell.api.platform.integration.job.persistence.spec.JobExecutionSpecifications
import net.blueshell.api.shared.enums.JobExecutionStatus
import net.blueshell.api.shared.service.BaseModelService
import net.blueshell.api.shared.tracking.Actor
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class JobExecutionService(
    private val jobExecutionRepository: JobExecutionRepository
) : BaseModelService<JobExecution, Long, JobExecutionRepository>(jobExecutionRepository) {
    @Transactional
    fun createQueued(
        jobType: String,
        payload: String?,
        actor: Actor,
        dedupKey: String? = null
    ): JobExecution? {
        if (dedupKey != null) {
            val active = jobExecutionRepository.existsByJobTypeAndDedupKeyAndStatusIn(
                jobType,
                dedupKey,
                listOf(JobExecutionStatus.QUEUED, JobExecutionStatus.RUNNING)
            )
            if (active) return null
        }

        val execution = JobExecution(
            jobType = jobType,
            status = JobExecutionStatus.QUEUED,
            payload = payload,
            attempts = 0,
            queuedAt = Instant.now(),
            dedupKey = dedupKey,
            initiatedByUserId = actor.userId,
            initiatedByType = actor.type,
            initiatedByRole = actor.role
        )
        return super.create(execution)
    }

    @Transactional(readOnly = true)
    fun findByFilter(pageable: Pageable, filter: JobExecutionQuery): Page<JobExecution> {
        val spec = JobExecutionSpecifications.fromFilter(filter)
        return jobExecutionRepository.findAll(spec, pageable)
    }

    @Transactional(readOnly = true)
    fun findByIdOrNull(id: Long): JobExecution? = jobExecutionRepository.findById(id).orElse(null)

    @Transactional(readOnly = true)
    fun countAllByStatus(): Map<JobExecutionStatus, Long> =
        JobExecutionStatus.entries.associateWith { jobExecutionRepository.countByStatus(it) }

    @Transactional(readOnly = true)
    fun findStaleRunning(threshold: Instant, pageable: Pageable): List<JobExecution> =
        jobExecutionRepository.findByStatusAndStartedAtBefore(JobExecutionStatus.RUNNING, threshold, pageable)

    @Transactional(readOnly = true)
    fun findStaleQueued(threshold: Instant, pageable: Pageable): List<JobExecution> =
        jobExecutionRepository.findByStatusAndNextAttemptAtIsNullAndQueuedAtBefore(
            JobExecutionStatus.QUEUED,
            threshold,
            pageable
        )

    @Transactional(readOnly = true)
    fun findDueScheduledRetries(now: Instant, pageable: Pageable): List<JobExecution> =
        jobExecutionRepository.findByStatusAndNextAttemptAtLessThanEqual(
            JobExecutionStatus.QUEUED,
            now,
            pageable
        )

    @Transactional
    fun resetRunningToQueued(execution: JobExecution): JobExecution {
        execution.status = JobExecutionStatus.QUEUED
        execution.startedAt = null
        execution.queuedAt = Instant.now()
        return super.update(execution)
    }

    @Transactional
    fun markRunning(execution: JobExecution): JobExecution {
        execution.status = JobExecutionStatus.RUNNING
        execution.startedAt = Instant.now()
        return super.update(execution)
    }

    @Transactional
    fun markSuccess(execution: JobExecution): JobExecution {
        execution.status = JobExecutionStatus.SUCCESS
        execution.finishedAt = Instant.now()
        execution.errorMessage = null
        execution.errorType = null
        execution.errorReason = null
        return super.update(execution)
    }

    @Transactional
    fun markFailed(
        execution: JobExecution,
        errorType: String,
        errorReason: String,
        stackTrace: String? = null
    ): JobExecution {
        execution.status = JobExecutionStatus.FAILED
        execution.finishedAt = Instant.now()
        applyErrorInfo(execution, errorType, errorReason, stackTrace)
        return super.update(execution)
    }

    @Transactional
    fun markDead(
        execution: JobExecution,
        errorType: String,
        errorReason: String,
        stackTrace: String? = null
    ): JobExecution {
        execution.status = JobExecutionStatus.DEAD
        execution.finishedAt = Instant.now()
        applyErrorInfo(execution, errorType, errorReason, stackTrace)
        return super.update(execution)
    }

    @Transactional
    fun markRetryScheduled(
        execution: JobExecution,
        errorType: String,
        errorReason: String,
        stackTrace: String? = null,
        nextAttemptAt: Instant
    ): JobExecution {
        execution.status = JobExecutionStatus.QUEUED
        execution.queuedAt = Instant.now()
        execution.startedAt = null
        execution.finishedAt = null
        execution.nextAttemptAt = nextAttemptAt
        applyErrorInfo(execution, errorType, errorReason, stackTrace)
        execution.attempts += 1
        return super.update(execution)
    }

    private fun applyErrorInfo(
        execution: JobExecution,
        errorType: String,
        errorReason: String,
        stackTrace: String?
    ) {
        execution.errorType = errorType
        execution.errorReason = stackTrace?.takeIf { it.isNotBlank() } ?: errorReason
        execution.errorMessage = "$errorType: $errorReason"
    }

    /**
     * Manual retry triggered from the admin UI. Preserves the attempt count
     * (incrementing it) and clears any pending retry schedule so the job runs
     * immediately.
     */
    @Transactional
    fun requeue(execution: JobExecution): JobExecution {
        execution.status = JobExecutionStatus.QUEUED
        execution.queuedAt = Instant.now()
        execution.startedAt = null
        execution.finishedAt = null
        execution.nextAttemptAt = null
        execution.errorMessage = null
        execution.errorType = null
        execution.errorReason = null
        execution.attempts += 1
        return super.update(execution)
    }

}
