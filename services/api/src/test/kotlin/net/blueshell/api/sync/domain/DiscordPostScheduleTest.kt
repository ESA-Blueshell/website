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
        trigger: Trigger = Trigger.MORNING,
        live: Boolean = true,
        endsAt: Instant = end,
    ) = DiscordPostSchedule.due(start, endsAt, live, at(now), trigger)

    @Test
    fun `announces at 08 00 Amsterdam time two weeks before the event's day, and not before`() {
        assertThat(DiscordPostSchedule.announceAt(start)).isEqualTo(at("2026-09-26T08:00"))
        assertThat(due("2026-09-26T07:59").announce).isFalse()
        assertThat(due("2026-09-26T08:00").announce).isTrue()
        assertThat(due("2026-10-02T08:00").announce).isTrue()
    }

    @Test
    fun `keeps to 08 00 Amsterdam time across the clocks going back`() {
        // Clocks go back on 25 October 2026; an event on 1 November is announced at 08:00 CEST.
        val november = at("2026-11-01T20:00")
        assertThat(DiscordPostSchedule.announceAt(november)).isEqualTo(Instant.parse("2026-10-18T06:00:00Z"))
    }

    @Test
    fun `leaves a late approval for the next morning, unless the event's day has come`() {
        assertThat(due("2026-10-05T10:00", Trigger.CHANGE).announce).isFalse()
        assertThat(due("2026-10-10T07:30", Trigger.CHANGE).announce).isFalse()
        assertThat(due("2026-10-10T10:00", Trigger.CHANGE).announce).isTrue()
        assertThat(due("2026-10-10T10:00", Trigger.CHANGE).dayPost).isTrue()
    }

    @Test
    fun `puts the day post up at 08 00 on the day, and takes it down the morning after the event ends`() {
        assertThat(due("2026-10-10T07:59").dayPost).isFalse()
        assertThat(due("2026-10-10T08:00").dayPost).isTrue()
        assertThat(due("2026-10-11T07:59").dayPost).isTrue()
        assertThat(due("2026-10-11T08:00").dayPost).isFalse()

        // Friday to Sunday: up through Sunday, down Monday morning.
        val sunday = at("2026-10-12T16:00")
        assertThat(due("2026-10-12T12:00", endsAt = sunday).dayPost).isTrue()
        assertThat(due("2026-10-13T08:00", endsAt = sunday).dayPost).isFalse()
    }

    @Test
    fun `posts nothing for an event that is over, or no longer live`() {
        assertThat(due("2026-10-10T23:00").announce).isFalse()
        assertThat(due("2026-10-10T12:00", live = false).announce).isFalse()
        assertThat(due("2026-10-10T12:00", live = false).dayPost).isFalse()
    }

    @Test
    fun `ends the Discord event once the event is over`() {
        assertThat(due("2026-10-10T22:59").over).isFalse()
        assertThat(due("2026-10-10T23:00").over).isTrue()
    }
}
