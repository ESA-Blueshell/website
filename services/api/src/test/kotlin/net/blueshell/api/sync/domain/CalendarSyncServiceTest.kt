package net.blueshell.api.sync.domain

import net.blueshell.api.event.api.CalendarAdapter
import net.blueshell.api.event.api.CalendarEventRef
import net.blueshell.api.event.api.EventService
import net.blueshell.api.sync.api.ExternalIdMappingService
import net.blueshell.api.sync.persistence.ExternalIdMapping
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.time.Instant

class CalendarSyncServiceTest {
    private val calendar: CalendarAdapter = mock()
    private val mappings: ExternalIdMappingService = mock()
    private val events: EventService = mock()
    private val service = CalendarSyncService(calendar, mappings, events)

    private fun stubEvent(
        approved: Boolean,
        deletedAt: Instant = Instant.parse("9999-12-31T23:59:59Z"),
    ) {
        val event =
            Entities
                .event(
                    approved = approved,
                    title = "LAN",
                    startTime = Instant.parse("2026-10-01T18:00:00Z"),
                    endTime = Instant.parse("2026-10-01T23:00:00Z"),
                ).also { it.deletedAt = deletedAt }
        whenever(events.findByIdIncludingDeletedOrNull(7L)).thenReturn(event)
    }

    private fun mapped(externalId: String) {
        whenever(mappings.find("EVENT", 7L, "GOOGLE_CALENDAR")).thenReturn(ExternalIdMapping("EVENT", 7L, "GOOGLE_CALENDAR", externalId))
    }

    @Test
    fun `an approved event not yet on the calendar is added and its id recorded`() {
        stubEvent(approved = true)
        whenever(calendar.addEvent(eq(7L), any())).thenReturn(CalendarEventRef("g1", null))

        assertThat(service.sync(7L)).isNull()

        verify(mappings).upsert("EVENT", 7L, "GOOGLE_CALENDAR", "g1")
    }

    @Test
    fun `an approved event on the calendar is updated in place`() {
        stubEvent(approved = true)
        mapped("g1")

        service.sync(7L)

        verify(calendar).updateEvent(eq(7L), eq("g1"), any())
        verify(calendar, never()).addEvent(any(), any())
        verify(mappings).upsert("EVENT", 7L, "GOOGLE_CALENDAR", "g1")
    }

    @Test
    fun `an unapproved event comes off the calendar`() {
        stubEvent(approved = false)
        mapped("g1")

        service.sync(7L)

        verify(calendar).removeEvent(7L, "g1")
        verify(mappings).upsert("EVENT", 7L, "GOOGLE_CALENDAR", null)
    }

    @Test
    fun `a deleted event never on the calendar touches nothing there`() {
        stubEvent(approved = true, deletedAt = Instant.parse("2026-09-01T00:00:00Z"))

        service.sync(7L)

        verifyNoInteractions(calendar)
        verify(mappings).upsert("EVENT", 7L, "GOOGLE_CALENDAR", null)
    }

    @Test
    fun `says why nothing was pushed for an event that no longer exists`() {
        assertThat(service.sync(7L)).isEqualTo("The event no longer exists.")

        verifyNoInteractions(calendar, mappings)
    }
}
