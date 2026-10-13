package net.blueshell.api.exceptions.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import net.blueshell.api.exceptions.api.ExceptionSource
import net.blueshell.api.shared.model.AutoIdEntity
import java.time.Instant

/** One fault: every exception of one type thrown from one place, and the latest of them. */
@Entity
@Table(name = "recorded_exception")
class RecordedException(
    @Column(nullable = false, length = 64)
    val fingerprint: String,
    @Column(name = "exception_type", nullable = false)
    val exceptionType: String,
    /** The class and method the fault was thrown from, without the line. */
    @Column(name = "thrown_at", nullable = false, length = 512)
    val thrownAt: String,
    @Column(name = "first_seen_at", nullable = false)
    val firstSeenAt: Instant,
    @Column(name = "last_seen_at", nullable = false)
    var lastSeenAt: Instant,
    @Column(nullable = false)
    var occurrences: Long,
    @Column(name = "latest_message", columnDefinition = "LONGTEXT")
    var latestMessage: String?,
    @Column(name = "latest_stack_trace", columnDefinition = "LONGTEXT")
    var latestStackTrace: String?,
    @Enumerated(EnumType.STRING)
    @Column(name = "latest_source", nullable = false, length = 16)
    var latestSource: ExceptionSource,
    @Column(name = "latest_concern", nullable = false, length = 512)
    var latestConcern: String,
    @Column(name = "latest_job_execution_id")
    var latestJobExecutionId: Long?,
    @Column(name = "resolved_at")
    var resolvedAt: Instant?,
) : AutoIdEntity()
