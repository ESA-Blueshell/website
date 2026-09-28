package net.blueshell.api.sync.domain

import net.blueshell.api.event.api.EventChange
import net.blueshell.api.event.api.EventChanged
import net.blueshell.api.shared.job.CalendarJobs
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

class CalendarSyncListenerTest {
    private val jobs: JobQueue = mock()
    private val listener = CalendarSyncListener(jobs)

    @Test
    fun `EventChanged enqueues a SyncCalendarEvent job`() {
        listener.on(EventChanged(42L, EventChange.CREATED))

        verify(
            jobs,
        ).runAsync(
            eq(CalendarJobs.SyncCalendarEvent),
            eq(CalendarJobs.SyncCalendarEventPayload(42L)),
            eq(JobTrigger.EVENT_CREATED),
            anyOrNull(),
        )
        verifyNoMoreInteractions(jobs)
    }
}
