package net.blueshell.api.jobs.persistence

import jakarta.persistence.LockModeType
import net.blueshell.api.shared.enums.JobExecutionStatus
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface JobExecutionRepository : BaseRepository<JobExecution, Long> {
    fun countByStatus(status: JobExecutionStatus): Long

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

    // A null dedup key matches no twin, which is right: createQueued folds only keyed jobs.
    @Query(
        """
        select j from JobExecution j
        where j.status = :queued
          and j.nextAttemptAt is null
          and j.queuedAt < :threshold
          and not exists (
              select r.id from JobExecution r
              where r.status = :running and r.jobType = j.jobType and r.dedupKey = j.dedupKey)
        order by j.queuedAt asc, j.id asc
        """,
    )
    fun findUndispatchedQueuedBefore(
        @Param("queued") queued: JobExecutionStatus,
        @Param("running") running: JobExecutionStatus,
        @Param("threshold") threshold: Instant,
        pageable: Pageable,
    ): List<JobExecution>

    fun findByStatusAndNextAttemptAtLessThanEqual(
        status: JobExecutionStatus,
        threshold: Instant,
        pageable: Pageable,
    ): List<JobExecution>

    // Null dedup keys are not twins: createQueued never folds jobs that carry none.
    @Query(
        """
        select j from JobExecution j
        where j.status = :status
          and j.jobType like concat(:prefix, '%')
          and (j.nextAttemptAt is null or j.nextAttemptAt <= :now)
          and (j.dedupKey is null or not exists (
              select r.id from JobExecution r
              where r.status = :running and r.jobType = j.jobType and r.dedupKey = j.dedupKey))
        order by j.queuedAt asc, j.id asc
        """,
    )
    fun findDueEmailJobs(
        @Param("prefix") prefix: String,
        @Param("status") status: JobExecutionStatus,
        @Param("running") running: JobExecutionStatus,
        @Param("now") now: Instant,
        pageable: Pageable,
    ): List<JobExecution>

    @Modifying
    @Query(
        """
        update JobExecution j
        set j.status = :status, j.startedAt = :now, j.nextAttemptAt = null
        where j.id = :id and j.status = :queued
        """,
    )
    fun markRunningIfQueued(
        @Param("id") id: Long,
        @Param("now") now: Instant,
        @Param("status") status: JobExecutionStatus,
        @Param("queued") queued: JobExecutionStatus,
    ): Int

    @Modifying
    @Query(
        """
        update JobExecution j
        set j.status = :queued, j.startedAt = null, j.queuedAt = :now
        where j.id = :id and j.status = :running and j.startedAt = :seenStartedAt
        """,
    )
    fun resetRunningIfStillRunning(
        @Param("id") id: Long,
        @Param("seenStartedAt") seenStartedAt: Instant,
        @Param("now") now: Instant,
        @Param("queued") queued: JobExecutionStatus,
        @Param("running") running: JobExecutionStatus,
    ): Int
}
