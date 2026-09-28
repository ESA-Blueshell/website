package net.blueshell.api.jobs.persistence

import jakarta.persistence.LockModeType
import jakarta.persistence.QueryHint
import net.blueshell.api.shared.enums.JobExecutionStatus
import net.blueshell.api.shared.repository.BaseRepository
import org.hibernate.jpa.HibernateHints
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.QueryHints
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

    /*
     * A locking read, so it sees a twin another transaction has just committed, and waits for one
     * it has inserted but not yet committed.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        "select j from JobExecution j where j.jobType = :jobType and j.dedupKey = :dedupKey " +
            "and j.status in :statuses order by j.id",
    )
    fun findTwinsForUpdate(
        @Param("jobType") jobType: String,
        @Param("dedupKey") dedupKey: String,
        @Param("statuses") statuses: Collection<JobExecutionStatus>,
    ): List<JobExecution>

    /*
     * A MariaDB named lock belongs to the connection, so both halves must run on one: inside the
     * caller's transaction. Answers 1 once held, 0 when [seconds] ran out. Neither flushes first:
     * after a failed insert a flush throws again, and a lock never released stays on the pooled
     * connection.
     */
    @QueryHints(QueryHint(name = HibernateHints.HINT_FLUSH_MODE, value = "COMMIT"))
    @Query(value = "SELECT GET_LOCK(:name, :seconds)", nativeQuery = true)
    fun acquireNamedLock(
        @Param("name") name: String,
        @Param("seconds") seconds: Int,
    ): Int?

    @QueryHints(QueryHint(name = HibernateHints.HINT_FLUSH_MODE, value = "COMMIT"))
    @Query(value = "SELECT RELEASE_LOCK(:name)", nativeQuery = true)
    fun releaseNamedLock(
        @Param("name") name: String,
    ): Int?

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
