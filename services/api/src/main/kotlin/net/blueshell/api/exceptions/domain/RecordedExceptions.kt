package net.blueshell.api.exceptions.domain

import net.blueshell.api.exceptions.persistence.RecordedException
import net.blueshell.api.exceptions.persistence.RecordedExceptionRepository
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Duration

/** Reads, resolves and forgets recorded faults. */
@Service
class RecordedExceptions(
    private val records: RecordedExceptionRepository,
    private val clock: Clock,
) {
    /** Newest first; `resolved` narrows to resolved or open faults, and null keeps both. */
    @Transactional(readOnly = true)
    fun list(resolved: Boolean?): List<RecordedException> =
        records.findAllByOrderByLastSeenAtDesc().filter { resolved == null || (it.resolvedAt != null) == resolved }

    @Transactional(readOnly = true)
    fun find(id: Long): RecordedException = records.findById(id).orElseThrow { notFound(id) }

    /** Marks the fault resolved until it fires again. */
    @Transactional
    fun resolve(id: Long): RecordedException {
        val fault = records.findById(id).orElseThrow { notFound(id) }
        fault.resolvedAt = clock.instant()
        return fault
    }

    /** Forgets faults that have not fired for [RETENTION]. */
    @Scheduled(cron = $$"${app.exceptions.purge-cron:0 45 3 * * *}")
    @Transactional
    fun purgeExpired(): Int = records.purgeLastSeenBefore(clock.instant().minus(RETENTION))

    private fun notFound(id: Long) = ResponseStatusException(HttpStatus.NOT_FOUND, "No recorded exception with id $id")

    companion object {
        val RETENTION: Duration = Duration.ofDays(90)
    }
}
