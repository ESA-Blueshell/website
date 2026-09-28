package net.blueshell.api.sync.domain

import net.blueshell.api.event.api.EventService
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.sync.api.ExternalIdMappingService
import net.blueshell.api.sync.persistence.ExternalIdMapping
import net.blueshell.api.testsupport.runJob
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class CalendarSweepTest {
    private val jobs: JobQueue = mock()
    private val events: EventService = mock()
    private val mappings: ExternalIdMappingService = mock()
    private val objectMapper = JsonMapper()
    private val now = Instant.parse("2026-10-01T12:00:00Z")
    private val sweep =
        SyncUnsyncedEventsJob(objectMapper, events, mappings, jobs).also {
            it.clock = Clock.fixed(now, ZoneOffset.UTC)
        }

    @Test
    fun `the scheduler queues one sweep`() {
        CalendarSyncScheduler(jobs).syncUnsyncedEvents()

        verify(jobs).runAsync(
            eq(CalendarJobs.SyncUnsyncedEvents),
            eq(CalendarJobs.SyncUnsyncedEventsPayload()),
            eq(JobTrigger.SCHEDULED_RUN),
            anyOrNull(),
        )
    }

    @Test
    fun `the sweep queues a sync for each event that has not ended and was never synced`() {
        whenever(events.idsEndingFrom(now)).thenReturn(listOf(1L, 2L))
        whenever(mappings.findBatch("EVENT", listOf(1L, 2L), "GOOGLE_CALENDAR"))
            .thenReturn(listOf(ExternalIdMapping("EVENT", 1L, "GOOGLE_CALENDAR", "g1")))

        sweep.runJob(objectMapper.writeValueAsString(CalendarJobs.SyncUnsyncedEventsPayload()))

        verify(jobs).runAsync(
            eq(CalendarJobs.SyncCalendarEvent),
            eq(CalendarJobs.SyncCalendarEventPayload(2L)),
            eq(JobTrigger.ANOTHER_JOB),
            anyOrNull(),
        )
        verify(jobs, never()).runAsync(
            eq(CalendarJobs.SyncCalendarEvent),
            eq(CalendarJobs.SyncCalendarEventPayload(1L)),
            eq(JobTrigger.ANOTHER_JOB),
            anyOrNull(),
        )
    }
}
