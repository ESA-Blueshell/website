package net.blueshell.api.exceptions.web

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.exceptions.api.ExceptionSource
import net.blueshell.api.exceptions.persistence.RecordedException
import java.time.Instant

/** One fault as the list and its own page read it; the stack trace rides only on the page. */
@Schema(name = "RecordedException")
data class RecordedExceptionDTO(
    val id: Long,
    val exceptionType: String,
    val thrownAt: String,
    val firstSeenAt: Instant,
    val lastSeenAt: Instant,
    val occurrences: Long,
    val latestMessage: String?,
    val latestSource: ExceptionSource,
    val latestConcern: String,
    val latestJobExecutionId: Long?,
    val resolvedAt: Instant?,
    val latestStackTrace: String?,
)

fun RecordedException.toDto(withTrace: Boolean): RecordedExceptionDTO =
    RecordedExceptionDTO(
        id = requireNotNull(id),
        exceptionType = exceptionType,
        thrownAt = thrownAt,
        firstSeenAt = firstSeenAt,
        lastSeenAt = lastSeenAt,
        occurrences = occurrences,
        latestMessage = latestMessage,
        latestSource = latestSource,
        latestConcern = latestConcern,
        latestJobExecutionId = latestJobExecutionId,
        resolvedAt = resolvedAt,
        latestStackTrace = if (withTrace) latestStackTrace else null,
    )
