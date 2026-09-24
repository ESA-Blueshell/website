package net.blueshell.api.sync.domain

import net.blueshell.api.event.api.EventService
import net.blueshell.api.event.persistence.Event
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class CalendarSyncServiceTest {
    private val fanOut: SyncFanOut = mock()
    private val events: EventService = mock()
    private val registry: SyncTargetRegistry = mock { on { forCalendar() }.thenReturn(emptyList()) }
    private val service = CalendarSyncService(registry, fanOut, events)

    @Test
    fun `pushes an event that exists`() {
        val event: Event =
            mock {
                on { approved }.thenReturn(false)
                on { deletedAt }.thenReturn(Instant.parse("9999-12-31T23:59:59Z"))
            }
        whenever(events.findByIdIncludingDeletedOrNull(7L)).thenReturn(event)

        assertThat(service.sync(7L)).isNull()

        verify(fanOut).push<Any>(eq("EVENT"), eq(7L), anyOrNull(), any(), any())
    }

    @Test
    fun `says why nothing was pushed for an event that no longer exists`() {
        assertThat(service.sync(7L)).isEqualTo("The event no longer exists.")

        verify(fanOut, never()).push<Any>(any(), any(), anyOrNull(), any(), any())
    }
}
