package net.blueshell.api.sync.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class GoogleCalendarEventsTest {
    private val start = Instant.parse("2026-10-01T17:00:00.250Z")
    private val end = Instant.parse("2026-10-01T20:00:00Z")

    @Test
    fun `renders the description's Markdown, tables and strikethrough included, without paragraph tags`() {
        val event = toGoogleEvent("LAN", "Horst", "**Bring** a ~~laptop~~ PC\n\n| a | b |\n|---|---|\n| 1 | 2 |", start, end)

        assertThat(event.description).contains("<strong>Bring</strong>", "<del>laptop</del>", "<table>")
        assertThat(event.description).doesNotContain("<p>", "</p>")
    }

    @Test
    fun `leaves the description unset when the event has none`() {
        assertThat(toGoogleEvent("LAN", null, null, start, end).description).isNull()
    }

    @Test
    fun `carries the title, the place and the times in whole seconds, in Amsterdam time`() {
        val event = toGoogleEvent("LAN", "Horst", null, start, end)

        assertThat(event.summary).isEqualTo("LAN")
        assertThat(event.location).isEqualTo("Horst")
        assertThat(event.start.dateTime.value).isEqualTo(Instant.parse("2026-10-01T17:00:00Z").toEpochMilli())
        assertThat(event.end.dateTime.value).isEqualTo(end.toEpochMilli())
        assertThat(event.start.timeZone).isEqualTo("Europe/Amsterdam")
    }
}
