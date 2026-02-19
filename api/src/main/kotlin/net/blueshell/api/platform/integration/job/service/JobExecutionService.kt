package net.blueshell.api.platform.integration.job.service

import net.blueshell.api.platform.integration.job.model.JobExecution
import net.blueshell.api.platform.integration.job.repository.JobExecutionRepository
import net.blueshell.api.shared.enums.JobExecutionStatus
import net.blueshell.api.shared.service.BaseModelService
import net.blueshell.api.shared.tracking.Actor
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
        actor: Actor
    ): JobExecution {
        val execution = JobExecution(
            jobType = jobType,
            status = JobExecutionStatus.QUEUED,
            payload = payload,
            attempts = 0,
            queuedAt = Instant.now(),
            initiatedByUserId = actor.userId,
            initiatedByType = actor.type,
            initiatedByRole = actor.role
        )
        return super.create(execution)
    }

    @Transactional(readOnly = true)
    fun findRecent(): List<JobExecution> = jobExecutionRepository.findTop200ByOrderByCreatedAtDesc()

    @Transactional(readOnly = true)
    fun findByIdOrNull(id: Long): JobExecution? = jobExecutionRepository.findById(id).orElse(null)

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
    fun markFailed(execution: JobExecution, errorType: String, errorReason: String): JobExecution {
        execution.status = JobExecutionStatus.FAILED
        execution.finishedAt = Instant.now()
        execution.errorType = errorType
        execution.errorReason = errorReason
        execution.errorMessage = "$errorType: $errorReason"
        return super.update(execution)
    }

    @Transactional
    fun markRetryQueued(execution: JobExecution, errorType: String, errorReason: String): JobExecution {
        execution.status = JobExecutionStatus.QUEUED
        execution.queuedAt = Instant.now()
        execution.startedAt = null
        execution.finishedAt = null
        execution.errorType = errorType
        execution.errorReason = errorReason
        execution.errorMessage = "$errorType: $errorReason"
        execution.attempts += 1
        return super.update(execution)
    }

    @Transactional
    fun requeue(execution: JobExecution): JobExecution {
        execution.status = JobExecutionStatus.QUEUED
        execution.queuedAt = Instant.now()
        execution.startedAt = null
        execution.finishedAt = null
        execution.errorMessage = null
        execution.errorType = null
        execution.errorReason = null
        execution.attempts += 1
        return super.update(execution)
    }
}
