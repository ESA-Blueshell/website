package net.blueshell.api.sync.domain

import net.blueshell.api.domain.event.application.calendar.CalendarAdapter
import net.blueshell.api.domain.event.application.calendar.CalendarEventData
import net.blueshell.api.domain.event.application.calendar.CalendarEventRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class GoogleCalendarEventSyncTargetTest {

    private val adapter: CalendarAdapter = mock()
    private val target = GoogleCalendarEventSyncTarget(adapter)
    private val data = CalendarEventData(
        title = "t", location = null, description = null,
        startTime = Instant.EPOCH, endTime = Instant.EPOCH, approved = true,
    )

    @Test
    fun `null data and null id is a no-op`() {
        assertNull(target.push(1L, null, null))
        verify(adapter, never()).syncEvent(any(), any(), any())
        verify(adapter, never()).removeEvent(any(), any())
    }

    @Test
    fun `null data with existing id removes and returns null`() {
        assertNull(target.push(1L, null, "gid"))
        verify(adapter).removeEvent(1L, "gid")
    }

    @Test
    fun `data delegates to syncEvent and returns the external id`() {
        whenever(adapter.syncEvent(eq(1L), eq(data), eq(null)))
            .thenReturn(CalendarEventRef("gid", null))
        assertEquals("gid", target.push(1L, data, null))
    }

    @Test
    fun `data with existing id passes the id through`() {
        whenever(adapter.syncEvent(eq(1L), eq(data), eq("gid")))
            .thenReturn(CalendarEventRef("gid", null))
        assertEquals("gid", target.push(1L, data, "gid"))
    }
}
