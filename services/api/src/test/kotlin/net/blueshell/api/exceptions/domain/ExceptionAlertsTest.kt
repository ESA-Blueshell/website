package net.blueshell.api.exceptions.domain

import net.blueshell.api.alerts.api.AlertAudience
import net.blueshell.api.alerts.api.AlertKind
import net.blueshell.api.alerts.api.RaisedAlert
import net.blueshell.api.exceptions.api.ExceptionSource
import net.blueshell.api.exceptions.persistence.RecordedException
import net.blueshell.api.exceptions.persistence.RecordedExceptionRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

class ExceptionAlertsTest {
    private val records: RecordedExceptionRepository = mock()
    private val alerts = ExceptionAlerts(records)

    private fun open(lastSeen: Instant) =
        RecordedException("f", "T", "P.m", lastSeen, lastSeen, 1, null, null, ExceptionSource.JOB, "job", null, null)

    @Test
    fun `open faults raise one admin alert, keyed by the latest occurrence`() {
        val later = Instant.parse("2026-09-30T10:00:00Z")
        whenever(records.findAllByResolvedAtIsNull()).thenReturn(listOf(open(later.minusSeconds(5)), open(later)))

        assertThat(alerts.audience).isEqualTo(AlertAudience.ADMIN)
        assertThat(alerts.raised())
            .containsExactly(RaisedAlert("exception-open:${later.toEpochMilli()}", AlertKind.EXCEPTION_OPEN, null, null, 2, later))
    }

    @Test
    fun `no open fault raises nothing`() {
        assertThat(alerts.raised()).isEmpty()
    }
}
