package net.blueshell.api.exceptions.domain

import net.blueshell.api.exceptions.api.ExceptionSource
import net.blueshell.api.exceptions.persistence.RecordedException
import net.blueshell.api.exceptions.persistence.RecordedExceptionRepository
import net.blueshell.api.exceptions.web.RecordedExceptionController
import net.blueshell.api.exceptions.web.toDto
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional

class RecordedExceptionsTest {
    private val records: RecordedExceptionRepository = mock()
    private val now = Instant.parse("2026-09-30T10:00:00Z")
    private val faults = RecordedExceptions(records, Clock.fixed(now, ZoneOffset.UTC))
    private val controller = RecordedExceptionController(faults)

    private fun fault(
        id: Long,
        resolvedAt: Instant? = null,
    ) = RecordedException(
        fingerprint = "f$id",
        exceptionType = "java.lang.IllegalStateException",
        thrownAt = "net.blueshell.api.Thing.run",
        firstSeenAt = now,
        lastSeenAt = now,
        occurrences = 3,
        latestMessage = "broke",
        latestStackTrace = "trace",
        latestSource = ExceptionSource.JOB,
        latestConcern = "contact.sync",
        latestJobExecutionId = 9,
        resolvedAt = resolvedAt,
    ).apply { this.id = id }

    @Test
    fun `lists open, resolved or every fault, newest first, without their traces`() {
        whenever(records.findAllByOrderByLastSeenAtDesc()).thenReturn(listOf(fault(1), fault(2, resolvedAt = now)))

        assertThat(controller.listExceptions(null).map { it.id }).containsExactly(1, 2)
        assertThat(controller.listExceptions(false).map { it.id }).containsExactly(1)
        assertThat(controller.listExceptions(true).map { it.id }).containsExactly(2)
        assertThat(controller.listExceptions(null).first().latestStackTrace).isNull()
    }

    @Test
    fun `opens one fault with its trace, and refuses one that is not there`() {
        whenever(records.findById(1)).thenReturn(Optional.of(fault(1)))
        whenever(records.findById(2)).thenReturn(Optional.empty())

        val one = controller.findException(1)
        assertThat(one.latestStackTrace).isEqualTo("trace")
        assertThat(
            JsonMapper
                .builder()
                .findAndAddModules()
                .build()
                .writeValueAsString(one),
        ).contains(
            "\"thrownAt\":\"net.blueshell.api.Thing.run\"",
            "\"occurrences\":3",
            "\"latestJobExecutionId\":9",
            "\"latestConcern\":\"contact.sync\"",
        )
        assertThatThrownBy { controller.findException(2) }.isInstanceOf(ResponseStatusException::class.java)
        assertThatThrownBy { controller.resolveException(2) }.isInstanceOf(ResponseStatusException::class.java)
    }

    @Test
    fun `resolving stamps the moment`() {
        whenever(records.findById(1)).thenReturn(Optional.of(fault(1)))

        assertThat(controller.resolveException(1).resolvedAt).isEqualTo(now)
    }

    @Test
    fun `forgets faults quiet for longer than the retention period`() {
        whenever(records.purgeLastSeenBefore(now.minus(RecordedExceptions.RETENTION))).thenReturn(4)

        assertThat(faults.purgeExpired()).isEqualTo(4)
        verify(records).purgeLastSeenBefore(now.minus(RecordedExceptions.RETENTION))
        assertThat(fault(5).toDto(withTrace = true).firstSeenAt).isEqualTo(now)
    }

    @Test
    fun `hibernate can build an empty fault to fill`() {
        val empty = RecordedException::class.java.getDeclaredConstructor().newInstance()
        assertThat(empty.id).isNull()
        assertThat(fault(6).fingerprint).isEqualTo("f6")
    }
}
