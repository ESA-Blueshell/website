package net.blueshell.api.event.api

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class AnnounceMorningTest {
    private fun at(local: String): Instant = LocalDateTime.parse(local).atZone(ZoneId.of("Europe/Amsterdam")).toInstant()

    @Test
    fun `is today's 08 00 Amsterdam time before it, and tomorrow's from 08 00 on`() {
        assertThat(AnnounceMorning.next(at("2026-10-01T06:30"))).isEqualTo(at("2026-10-01T08:00"))
        assertThat(AnnounceMorning.next(at("2026-10-01T08:00"))).isEqualTo(at("2026-10-02T08:00"))
        assertThat(AnnounceMorning.next(at("2026-10-01T14:00"))).isEqualTo(at("2026-10-02T08:00"))
    }

    @Test
    fun `keeps to 08 00 Amsterdam time across the clocks going back`() {
        // Clocks go back on 25 October 2026: the morning before is 06:00 UTC, the morning after 07:00.
        assertThat(AnnounceMorning.next(at("2026-10-24T14:00"))).isEqualTo(Instant.parse("2026-10-25T07:00:00Z"))
        assertThat(AnnounceMorning.next(at("2026-10-23T14:00"))).isEqualTo(Instant.parse("2026-10-24T06:00:00Z"))
    }
}
