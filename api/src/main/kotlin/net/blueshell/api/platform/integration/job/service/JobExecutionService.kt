package net.blueshell.api.platform.integration.job.service

import net.blueshell.api.platform.integration.job.model.JobExecution
import net.blueshell.api.shared.enums.JobExecutionStatus
import net.blueshell.api.platform.integration.job.repository.JobExecutionRepository
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class JobExecutionService(
    private val jobExecutionRepository: JobExecutionRepository
) {
    fun createQueued(jobType: String, payload: String?): JobExecution {
        val execution = JobExecution(
            jobType = jobType,
            status = JobExecutionStatus.QUEUED,
            payload = payload,
            attempts = 0,
            queuedAt = Instant.now()
        )
        return jobExecutionRepository.save(execution)
    }

    fun findById(id: Long): JobExecution? = jobExecutionRepository.findById(id).orElse(null)

    fun findRecent(): List<JobExecution> = jobExecutionRepository.findTop200ByOrderByCreatedAtDesc()

    fun markRunning(execution: JobExecution): JobExecution {
        execution.status = JobExecutionStatus.RUNNING
        execution.startedAt = Instant.now()
        return jobExecutionRepository.save(execution)
    }

    fun markSuccess(execution: JobExecution): JobExecution {
        execution.status = JobExecutionStatus.SUCCESS
        execution.finishedAt = Instant.now()
        execution.errorMessage = null
        return jobExecutionRepository.save(execution)
    }

    fun markFailed(execution: JobExecution, error: String): JobExecution {
        execution.status = JobExecutionStatus.FAILED
        execution.finishedAt = Instant.now()
        execution.errorMessage = error
        return jobExecutionRepository.save(execution)
    }

    fun requeue(execution: JobExecution): JobExecution {
        execution.status = JobExecutionStatus.QUEUED
        execution.queuedAt = Instant.now()
        execution.startedAt = null
        execution.finishedAt = null
        execution.errorMessage = null
        execution.attempts += 1
        return jobExecutionRepository.save(execution)
    }
}
