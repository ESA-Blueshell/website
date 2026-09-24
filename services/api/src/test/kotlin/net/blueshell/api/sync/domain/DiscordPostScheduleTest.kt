package net.blueshell.api.sync.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class DiscordPostScheduleTest {
    private val amsterdam = ZoneId.of("Europe/Amsterdam")

    private fun at(local: String): Instant = LocalDateTime.parse(local).atZone(amsterdam).toInstant()

    /* Saturday 10 October 2026, 20:00 to 23:00, a fortnight after Saturday 26 September. */
    private val start = at("2026-10-10T20:00")
    private val end = at("2026-10-10T23:00")

    private fun due(
        now: String,
        endsAt: Instant = end,
    ) = DiscordPostSchedule.due(start, endsAt, at(now))

    @Test
    fun `announces at 08 00 Amsterdam time two weeks before the event's day, and not before`() {
        assertThat(DiscordPostSchedule.infoPostAt(start)).isEqualTo(at("2026-09-26T08:00"))
        assertThat(due("2026-09-26T07:59").infoPost).isFalse()
        assertThat(due("2026-09-26T08:00").infoPost).isTrue()
        assertThat(due("2026-10-02T08:00").infoPost).isTrue()
    }

    @Test
    fun `keeps to 08 00 Amsterdam time across the clocks going back`() {
        // Clocks go back on 25 October 2026; an event on 1 November is announced at 08:00 CEST.
        val november = at("2026-11-01T20:00")
        assertThat(DiscordPostSchedule.infoPostAt(november)).isEqualTo(Instant.parse("2026-10-18T06:00:00Z"))
    }

    @Test
    fun `says when the event's own day has come, from 08 00`() {
        assertThat(due("2026-10-05T10:00").firstDayHasCome).isFalse()
        assertThat(due("2026-10-10T07:59").firstDayHasCome).isFalse()
        assertThat(due("2026-10-10T08:00").firstDayHasCome).isTrue()
    }

    @Test
    fun `puts the day post up at 08 00 on the day, and takes it down the morning after the event ends`() {
        assertThat(due("2026-10-10T07:59").calendarPost).isFalse()
        assertThat(due("2026-10-10T08:00").calendarPost).isTrue()
        assertThat(due("2026-10-11T07:59").calendarPost).isTrue()
        assertThat(due("2026-10-11T08:00").calendarPost).isFalse()

        // Friday to Sunday: up through Sunday, down Monday morning.
        val sunday = at("2026-10-12T16:00")
        assertThat(due("2026-10-12T12:00", endsAt = sunday).calendarPost).isTrue()
        assertThat(due("2026-10-13T08:00", endsAt = sunday).calendarPost).isFalse()

        // Over at 02:00 on the Sunday: down at 08:00 that Sunday, not the Monday.
        val small = at("2026-10-11T02:00")
        assertThat(due("2026-10-11T07:59", endsAt = small).calendarPost).isTrue()
        assertThat(due("2026-10-11T08:00", endsAt = small).calendarPost).isFalse()
        assertThat(due("2026-10-11T07:59", endsAt = at("2026-10-11T08:00")).calendarPost).isTrue()
    }

    @Test
    fun `posts no events-info post for an event that is over`() {
        assertThat(due("2026-10-10T23:00").infoPost).isFalse()
    }

    @Test
    fun `ends the Discord event once the event is over`() {
        assertThat(due("2026-10-10T22:59").over).isFalse()
        assertThat(due("2026-10-10T23:00").over).isTrue()
    }
}
