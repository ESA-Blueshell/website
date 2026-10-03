package net.blueshell.api.exceptions.api

import net.blueshell.api.exceptions.persistence.RecordedExceptionRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ExceptionRecorderTest {
    private val records: RecordedExceptionRepository = mock()
    private val transactions: PlatformTransactionManager =
        mock { on { getTransaction(any()) } doReturn SimpleTransactionStatus() }
    private val now = Instant.parse("2026-09-30T10:00:00Z")
    private val recorder = ExceptionRecorder(records, transactions, Clock.fixed(now, ZoneOffset.UTC))

    private val concern = ExceptionConcern(ExceptionSource.REQUEST, "GET /events/{id}", null)

    @Test
    fun `records one occurrence against the fault's fingerprint`() {
        val thrown = IllegalStateException("broke")

        recorder.record(thrown, concern)

        val thrownAt = "${ExceptionRecorderTest::class.java.name}.records one occurrence against the fault's fingerprint"
        verify(records).recordOccurrence(
            fingerprint = eq(ExceptionRecorder.fingerprint("java.lang.IllegalStateException", thrownAt)),
            exceptionType = eq("java.lang.IllegalStateException"),
            thrownAt = eq(thrownAt),
            seenAt = eq(now),
            message = eq("broke"),
            stackTrace = any(),
            source = eq("REQUEST"),
            concern = eq("GET /events/{id}"),
            jobExecutionId = eq(null),
        )
        assertThat(concern.source).isEqualTo(ExceptionSource.REQUEST)
        assertThat(concern.jobExecutionId).isNull()
    }

    @Test
    fun `a failure while recording is dropped rather than thrown`() {
        whenever(records.recordOccurrence(any(), any(), any(), any(), anyOrNull(), any(), any(), any(), anyOrNull()))
            .thenThrow(IllegalStateException("database down"))

        recorder.record(RuntimeException("first"), concern)
    }

    @Test
    fun `an exception raised while recording is not recorded again`() {
        whenever(records.recordOccurrence(any(), any(), any(), any(), anyOrNull(), any(), any(), any(), anyOrNull())).thenAnswer {
            recorder.record(RuntimeException("inside"), concern)
            1
        }

        recorder.record(RuntimeException("outside"), concern)

        verify(records, times(1)).recordOccurrence(any(), any(), any(), any(), anyOrNull(), any(), any(), any(), anyOrNull())
    }

    @Test
    fun `a fault is placed at the api's own frame, else the first, without its line`() {
        val own =
            RuntimeException().apply {
                stackTrace =
                    arrayOf(frame("java.util.Objects", "require"), frame("net.blueshell.api.Thing", "run"))
            }
        val foreign = RuntimeException().apply { stackTrace = arrayOf(frame("java.util.Objects", "require")) }
        val bare = RuntimeException().apply { stackTrace = emptyArray() }

        assertThat(ExceptionRecorder.thrownAt(own)).isEqualTo("net.blueshell.api.Thing.run")
        assertThat(ExceptionRecorder.thrownAt(foreign)).isEqualTo("java.util.Objects.require")
        assertThat(ExceptionRecorder.thrownAt(bare)).isEqualTo("unknown")
        assertThat(ExceptionRecorder.fingerprint("a", "b")).hasSize(64).isEqualTo(ExceptionRecorder.fingerprint("a", "b"))
    }

    private fun frame(
        type: String,
        method: String,
    ) = StackTraceElement(type, method, "File.kt", 1)
}
