package net.blueshell.api.jobs.persistence

import net.blueshell.api.shared.enums.JobExecutionStatus
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.domain.Pageable
import java.time.Instant

interface JobExecutionRepository : BaseRepository<JobExecution, Long> {
    fun countByStatus(status: JobExecutionStatus): Long

    fun findByJobType(jobType: String): List<JobExecution>

    fun findByJobTypeAndDedupKey(jobType: String, dedupKey: String): List<JobExecution>

    fun findByJobTypeAndPayload(jobType: String, payload: String): List<JobExecution>

    fun existsByJobTypeAndDedupKeyAndStatusIn(
        jobType: String,
        dedupKey: String,
        statuses: Collection<JobExecutionStatus>
    ): Boolean

    fun findByStatusAndStartedAtBefore(
        status: JobExecutionStatus,
        threshold: Instant
    ): List<JobExecution>

    fun findByStatusAndStartedAtBefore(
        status: JobExecutionStatus,
        threshold: Instant,
        pageable: Pageable
    ): List<JobExecution>

    fun findByStatusAndQueuedAtBefore(
        status: JobExecutionStatus,
        threshold: Instant
    ): List<JobExecution>

    fun findByStatusAndQueuedAtBefore(
        status: JobExecutionStatus,
        threshold: Instant,
        pageable: Pageable
    ): List<JobExecution>

    fun findByStatusAndNextAttemptAtIsNullAndQueuedAtBefore(
        status: JobExecutionStatus,
        threshold: Instant,
        pageable: Pageable
    ): List<JobExecution>

    fun findByStatusAndNextAttemptAtLessThanEqual(
        status: JobExecutionStatus,
        threshold: Instant,
        pageable: Pageable
    ): List<JobExecution>
}
