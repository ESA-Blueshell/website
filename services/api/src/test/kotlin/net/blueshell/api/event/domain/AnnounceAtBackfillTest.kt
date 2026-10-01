package net.blueshell.api.event.domain

import net.blueshell.api.event.persistence.EventRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AnnounceAtBackfillTest {
    @Test
    fun `gives the approved events without an announce at the first 08 00 after the start`() {
        val events: EventRepository = mock { on { announceUnannouncedAt(any(), any()) } doReturn 3 }
        // 1 October 2026 at 14:00 in Amsterdam: the next 08:00 is the 2nd's, 06:00 UTC.
        val now = Instant.parse("2026-10-01T12:00:00Z")
        val backfill = AnnounceAtBackfill(events).apply { clock = Clock.fixed(now, ZoneOffset.UTC) }

        backfill.onReady()

        verify(events).announceUnannouncedAt(Instant.parse("2026-10-02T06:00:00Z"), now)
    }
}
