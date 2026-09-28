package net.blueshell.api.platform.integration.mock

import net.blueshell.api.event.api.CalendarEventData
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class MockCalendarAdapterTest {
    private val adapter = MockCalendarAdapter()

    @BeforeEach
    fun setUp() {
        adapter.clear()
    }

    @Test
    fun `addEvent stores event and returns mock reference`() {
        val ref = adapter.addEvent(1L, eventData(title = "Launch Party"))

        assertThat(ref.externalId).startsWith("mock-")
        assertThat(ref.externalUrl).contains(ref.externalId)
        assertThat(adapter.getEventCount()).isEqualTo(1)
        val stored = adapter.findByExternalId(ref.externalId)
        assertThat(stored).isNotNull
        assertThat(stored!!.title).isEqualTo("Launch Party")
        assertThat(stored.eventId).isEqualTo(1L)
    }

    @Test
    fun `updateEvent updates existing stored event`() {
        val ref = adapter.addEvent(2L, eventData(title = "Original"))

        adapter.updateEvent(2L, ref.externalId, eventData(title = "Updated"))

        val stored = adapter.findByExternalId(ref.externalId)
        assertThat(stored).isNotNull
        assertThat(stored!!.title).isEqualTo("Updated")
        assertThat(stored.eventId).isEqualTo(2L)
    }

    @Test
    fun `updateEvent throws for unknown external id`() {
        assertThatThrownBy {
            adapter.updateEvent(3L, "missing-id", eventData(title = "Ignored"))
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Cannot update missing event")
    }

    @Test
    fun `removeEvent deletes known id and throws for unknown id`() {
        val ref = adapter.addEvent(4L, eventData(title = "To Remove"))
        assertThat(adapter.getEventCount()).isEqualTo(1)

        adapter.removeEvent(4L, ref.externalId)
        assertThat(adapter.getEventCount()).isZero()

        assertThatThrownBy {
            adapter.removeEvent(4L, "missing-id")
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Cannot remove missing event")
    }

    private fun eventData(title: String): CalendarEventData {
        val start = Instant.parse("2026-03-01T10:00:00Z")
        val end = Instant.parse("2026-03-01T12:00:00Z")
        return CalendarEventData(
            title = title,
            location = "Enschede",
            description = "Calendar test event",
            startTime = start,
            endTime = end,
        )
    }
}
