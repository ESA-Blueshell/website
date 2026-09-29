package net.blueshell.api.sync.domain

import net.blueshell.api.event.api.CalendarEventData
import net.blueshell.api.event.api.CalendarServiceException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import java.io.IOException
import java.time.Instant

/** The adapter against a client that keeps its calendar in memory, as Google would. */
class GoogleCalendarAdapterRoundTripTest {
    private class InMemoryGoogleCalendar : GoogleCalendarClient("calendar", "", MockEnvironment()) {
        val events = mutableMapOf<String, String>()
        private var next = 0

        override fun addEvent(
            title: String,
            location: String?,
            description: String?,
            startTime: Instant,
            endTime: Instant,
        ): GoogleCalendarEventResult {
            val id = "g${++next}"
            events[id] = title
            return GoogleCalendarEventResult(id, "https://calendar.example/$id")
        }

        override fun updateEvent(
            googleEventId: String,
            title: String,
            location: String?,
            description: String?,
            startTime: Instant,
            endTime: Instant,
        ) {
            if (googleEventId !in events) throw IOException("404")
            events[googleEventId] = title
        }

        override fun removeEvent(googleEventId: String) {
            events.remove(googleEventId) ?: throw IOException("404")
        }
    }

    private val calendar = InMemoryGoogleCalendar()
    private val adapter = GoogleCalendarAdapter(calendar) { null }

    private fun event(title: String) =
        CalendarEventData(
            title = title,
            location = "Campus",
            description = null,
            startTime = Instant.parse("2026-10-10T18:00:00Z"),
            endTime = Instant.parse("2026-10-10T21:00:00Z"),
        )

    @Test
    fun `an event is added, renamed and taken off again`() {
        val added = adapter.addEvent(1, event("LAN party"))
        assertThat(calendar.events).containsEntry(added.externalId, "LAN party")

        adapter.updateEvent(1, added.externalId, event("LAN party, day two"))
        assertThat(calendar.events).containsEntry(added.externalId, "LAN party, day two")

        adapter.removeEvent(1, added.externalId)
        assertThat(calendar.events).isEmpty()
    }

    @Test
    fun `an event the calendar no longer has fails in the calendar's own terms`() {
        assertThatThrownBy { adapter.updateEvent(1, "gone", event("LAN party")) }.isInstanceOf(CalendarServiceException::class.java)
        assertThatThrownBy { adapter.removeEvent(1, "gone") }.isInstanceOf(CalendarServiceException::class.java)
    }
}
