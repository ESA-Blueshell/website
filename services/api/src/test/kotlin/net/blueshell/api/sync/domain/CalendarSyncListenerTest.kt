package net.blueshell.api.sync.domain

import net.blueshell.api.domain.event.application.event.EventChange
import net.blueshell.api.domain.event.application.event.EventChanged
import net.blueshell.api.shared.job.CalendarJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

class CalendarSyncListenerTest {

    private val jobs: TrackedJobDispatcher = mock()
    private val listener = CalendarSyncListener(jobs)

    @Test
    fun `EventChanged enqueues a SyncCalendarEvent job`() {
        listener.on(EventChanged(42L, EventChange.CREATED))

        verify(jobs).runAsync(eq(CalendarJobs.SyncCalendarEvent), eq(CalendarJobs.SyncCalendarEventPayload(42L)))
        verifyNoMoreInteractions(jobs)
    }
}
