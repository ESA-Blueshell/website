package net.blueshell.api.sync.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class DiscordPostScheduleTest {
    private val amsterdam = ZoneId.of("Europe/Amsterdam")

    private fun at(local: String): Instant = LocalDateTime.parse(local).atZone(amsterdam).toInstant()

    // Saturday 10 October 2026, 20:00 to 23:00, a fortnight after Saturday 26 September.
    private val start = at("2026-10-10T20:00")
    private val end = at("2026-10-10T23:00")

    // The board approved it on 1 September, choosing the next morning.
    private val announceAt = at("2026-09-02T08:00")

    private fun due(
        now: String,
        endsAt: Instant = end,
        announcing: Instant? = announceAt,
    ) = DiscordPostSchedule.due(start, endsAt, announcing, at(now))

    @Test
    fun `announces from the time the board chose, however far ahead the event is`() {
        assertThat(due("2026-09-02T07:59").announce).isFalse()
        assertThat(due("2026-09-02T08:00").announce).isTrue()
        assertThat(due("2026-10-02T14:00").announce).isTrue()
    }

    @Test
    fun `announces at once where the event starts before the time chosen, and never without one`() {
        assertThat(due("2026-10-10T12:00", announcing = at("2026-10-11T08:00")).announce).isTrue()
        assertThat(due("2026-10-10T12:00", announcing = start).announce).isTrue()
        assertThat(due("2026-10-10T12:00", announcing = null).announce).isFalse()
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
    fun `says when the day post has come down for good`() {
        assertThat(due("2026-10-05T10:00").calendarPostOver).isFalse()
        assertThat(due("2026-10-11T07:59").calendarPostOver).isFalse()
        assertThat(due("2026-10-11T08:00").calendarPostOver).isTrue()
    }

    @Test
    fun `announces nothing once the event is over`() {
        assertThat(due("2026-10-10T22:59").announce).isTrue()
        assertThat(due("2026-10-10T23:00").announce).isFalse()
    }

    @Test
    fun `ends the Discord event once the event is over`() {
        assertThat(due("2026-10-10T22:59").over).isFalse()
        assertThat(due("2026-10-10T23:00").over).isTrue()
    }

    @Test
    fun `keeps a running event's announcement and its day post due, on every day it runs`() {
        // Friday to Sunday: still due on the Saturday, when an event approved then gets its posts.
        val sunday = at("2026-10-12T16:00")
        assertThat(due("2026-10-11T09:00", endsAt = sunday).announce).isTrue()
        assertThat(due("2026-10-11T09:00", endsAt = sunday).calendarPost).isTrue()
    }
}
