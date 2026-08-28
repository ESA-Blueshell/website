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
        val ref = adapter.addEvent(1L, eventData(title = "Launch Party", approved = true))

        assertThat(ref.externalId).startsWith("mock-")
        assertThat(ref.externalUrl).contains(ref.externalId)
        assertThat(adapter.getEventCount()).isEqualTo(1)
        val stored = adapter.findByExternalId(ref.externalId)
        assertThat(stored).isNotNull
        assertThat(stored!!.title).isEqualTo("Launch Party")
        assertThat(stored.eventId).isEqualTo(1L)
        assertThat(stored.approved).isTrue()
    }

    @Test
    fun `updateEvent updates existing stored event`() {
        val ref = adapter.addEvent(2L, eventData(title = "Original", approved = true))

        adapter.updateEvent(2L, ref.externalId, eventData(title = "Updated", approved = true))

        val stored = adapter.findByExternalId(ref.externalId)
        assertThat(stored).isNotNull
        assertThat(stored!!.title).isEqualTo("Updated")
        assertThat(stored.eventId).isEqualTo(2L)
        assertThat(stored.approved).isTrue()
    }

    @Test
    fun `updateEvent throws for unknown external id`() {
        assertThatThrownBy {
            adapter.updateEvent(3L, "missing-id", eventData(title = "Ignored", approved = true))
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Cannot update missing event")
    }

    @Test
    fun `removeEvent deletes known id and throws for unknown id`() {
        val ref = adapter.addEvent(4L, eventData(title = "To Remove", approved = true))
        assertThat(adapter.getEventCount()).isEqualTo(1)

        adapter.removeEvent(4L, ref.externalId)
        assertThat(adapter.getEventCount()).isZero()

        assertThatThrownBy {
            adapter.removeEvent(4L, "missing-id")
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Cannot remove missing event")
    }

    @Test
    fun `syncEvent updates existing external event when approved`() {
        val created = adapter.addEvent(5L, eventData(title = "Initial", approved = true))

        val result = adapter.syncEvent(5L, eventData(title = "Synced Update", approved = true), created.externalId)

        assertThat(result).isNotNull
        assertThat(result!!.externalId).isEqualTo(created.externalId)
        assertThat(adapter.findByExternalId(created.externalId)!!.title).isEqualTo("Synced Update")
    }

    @Test
    fun `syncEvent removes existing external event when not approved`() {
        val created = adapter.addEvent(6L, eventData(title = "Pending", approved = true))

        val result = adapter.syncEvent(6L, eventData(title = "Pending", approved = false), created.externalId)

        assertThat(result).isNull()
        assertThat(adapter.findByExternalId(created.externalId)).isNull()
    }

    @Test
    fun `syncEvent adds event when approved and no external id exists`() {
        val result = adapter.syncEvent(7L, eventData(title = "New Approved", approved = true), externalId = null)

        assertThat(result).isNotNull
        assertThat(adapter.getEventCount()).isEqualTo(1)
        val stored = adapter.findByExternalId(result!!.externalId)
        assertThat(stored).isNotNull
        assertThat(stored!!.eventId).isEqualTo(7L)
        assertThat(stored.title).isEqualTo("New Approved")
        assertThat(stored.approved).isTrue()
    }

    @Test
    fun `syncEvent does nothing when not approved and no external id exists`() {
        val result = adapter.syncEvent(8L, eventData(title = "Not Approved", approved = false), externalId = null)

        assertThat(result).isNull()
        assertThat(adapter.getEventCount()).isZero()
    }

    private fun eventData(title: String, approved: Boolean): CalendarEventData {
        val start = Instant.parse("2026-03-01T10:00:00Z")
        val end = Instant.parse("2026-03-01T12:00:00Z")
        return CalendarEventData(
            title = title,
            location = "Enschede",
            description = "Calendar test event",
            startTime = start,
            endTime = end,
            approved = approved
        )
    }
}
