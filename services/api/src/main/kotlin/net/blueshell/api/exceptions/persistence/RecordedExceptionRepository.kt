package net.blueshell.api.exceptions.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface RecordedExceptionRepository : JpaRepository<RecordedException, Long> {
    /**
     * Counts one occurrence against its fault in one statement, so two requests failing at once
     * cannot both create it. A resolved fault firing again reopens.
     */
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO recorded_exception (fingerprint, exception_type, thrown_at, first_seen_at, last_seen_at, occurrences,
                latest_message, latest_stack_trace, latest_source, latest_concern, latest_job_execution_id, resolved_at)
            VALUES (:fingerprint, :exceptionType, :thrownAt, :seenAt, :seenAt, 1,
                :message, :stackTrace, :source, :concern, :jobExecutionId, NULL)
            ON DUPLICATE KEY UPDATE
                last_seen_at = VALUES(last_seen_at),
                occurrences = occurrences + 1,
                latest_message = VALUES(latest_message),
                latest_stack_trace = VALUES(latest_stack_trace),
                latest_source = VALUES(latest_source),
                latest_concern = VALUES(latest_concern),
                latest_job_execution_id = VALUES(latest_job_execution_id),
                resolved_at = NULL
        """,
    )
    fun recordOccurrence(
        @Param("fingerprint") fingerprint: String,
        @Param("exceptionType") exceptionType: String,
        @Param("thrownAt") thrownAt: String,
        @Param("seenAt") seenAt: Instant,
        @Param("message") message: String?,
        @Param("stackTrace") stackTrace: String,
        @Param("source") source: String,
        @Param("concern") concern: String,
        @Param("jobExecutionId") jobExecutionId: Long?,
    ): Int

    fun findAllByOrderByLastSeenAtDesc(): List<RecordedException>

    fun findAllByResolvedAtIsNull(): List<RecordedException>

    fun findByFingerprint(fingerprint: String): RecordedException?

    @Modifying
    @Query("delete from RecordedException r where r.lastSeenAt < :cutoff")
    fun purgeLastSeenBefore(
        @Param("cutoff") cutoff: Instant,
    ): Int
}
