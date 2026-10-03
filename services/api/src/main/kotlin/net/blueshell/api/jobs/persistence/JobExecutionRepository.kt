package net.blueshell.api.jobs.persistence

import jakarta.persistence.LockModeType
import net.blueshell.api.shared.enums.JobExecutionStatus
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface JobExecutionRepository : BaseRepository<JobExecution, Long> {
    fun countByStatus(status: JobExecutionStatus): Long

    fun findTopByStatusOrderByIdDesc(status: JobExecutionStatus): JobExecution?

    fun findByJobType(jobType: String): List<JobExecution>

    fun findByJobTypeAndDedupKey(
        jobType: String,
        dedupKey: String,
    ): List<JobExecution>

    fun findByJobTypeAndPayload(
        jobType: String,
        payload: String,
    ): List<JobExecution>

    // By its key alone, so the lock covers that row and no gap beside it.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from JobExecution j where j.id = :id")
    fun findByIdForUpdate(
        @Param("id") id: Long,
    ): JobExecution?

    fun findByStatusAndStartedAtBefore(
        status: JobExecutionStatus,
        threshold: Instant,
    ): List<JobExecution>

    fun findByStatusAndStartedAtBefore(
        status: JobExecutionStatus,
        threshold: Instant,
        pageable: Pageable,
    ): List<JobExecution>

    fun findByStatusAndQueuedAtBefore(
        status: JobExecutionStatus,
        threshold: Instant,
    ): List<JobExecution>

    fun findByStatusAndQueuedAtBefore(
        status: JobExecutionStatus,
        threshold: Instant,
        pageable: Pageable,
    ): List<JobExecution>

    fun findByStatusAndNextAttemptAtIsNullAndQueuedAtBefore(
        status: JobExecutionStatus,
        threshold: Instant,
        pageable: Pageable,
    ): List<JobExecution>

    fun findByStatusAndNextAttemptAtLessThanEqual(
        status: JobExecutionStatus,
        threshold: Instant,
        pageable: Pageable,
    ): List<JobExecution>
}
