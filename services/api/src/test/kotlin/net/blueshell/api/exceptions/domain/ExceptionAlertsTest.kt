package net.blueshell.api.exceptions.domain

import net.blueshell.api.alerts.api.AlertAudience
import net.blueshell.api.alerts.api.AlertKind
import net.blueshell.api.alerts.api.RaisedAlert
import net.blueshell.api.exceptions.persistence.OpenFaults
import net.blueshell.api.exceptions.persistence.RecordedExceptionRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

class ExceptionAlertsTest {
    private val records: RecordedExceptionRepository = mock()
    private val alerts = ExceptionAlerts(records)

    @Test
    fun `open faults raise one admin alert, since the oldest was first seen and marked by the latest occurrence`() {
        val first = Instant.parse("2026-09-29T08:00:00Z")
        val later = Instant.parse("2026-09-30T10:00:00Z")
        whenever(records.openFaults()).thenReturn(open(2, first, later))

        assertThat(alerts.audience).isEqualTo(AlertAudience.ADMIN)
        assertThat(alerts.raised())
            .containsExactly(RaisedAlert("exception-open", AlertKind.EXCEPTION_OPEN, null, null, 2, first, mark = later.toEpochMilli()))
    }

    @Test
    fun `no open fault raises nothing`() {
        whenever(records.openFaults()).thenReturn(open(0, null, null))

        assertThat(alerts.raised()).isEmpty()
    }

    private fun open(
        faults: Long,
        oldest: Instant?,
        latest: Instant?,
    ) = object : OpenFaults {
        override val faults = faults
        override val oldest = oldest
        override val latest = latest
    }
}
