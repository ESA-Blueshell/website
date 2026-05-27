package net.blueshell.api.platform.integration.job.persistence.repository

import net.blueshell.api.platform.integration.job.persistence.JobExecution
import net.blueshell.api.shared.enums.JobExecutionStatus
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface JobExecutionRepository : BaseRepository<JobExecution, Long> {
    fun countByStatus(status: JobExecutionStatus): Long

    fun findByJobType(jobType: String): List<JobExecution>

    fun findByJobTypeAndDedupKey(jobType: String, dedupKey: String): List<JobExecution>

    /**
     * Native equality match on [JobExecution.payload]. A derived query binds the
     * `@Lob` payload as a CLOB, which MariaDB never matches against the LONGTEXT
     * column, so the comparison must run as native SQL with a plain string bind.
     * Mirrors the soft-delete `@SQLRestriction` since native SQL bypasses it.
     */
    @Query(
        value = "SELECT * FROM job_executions " +
            "WHERE job_type = :jobType AND payload = :payload " +
            "AND deleted_at = '9999-12-31 23:59:59'",
        nativeQuery = true,
    )
    fun findByJobTypeAndPayload(
        @Param("jobType") jobType: String,
        @Param("payload") payload: String,
    ): List<JobExecution>

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
