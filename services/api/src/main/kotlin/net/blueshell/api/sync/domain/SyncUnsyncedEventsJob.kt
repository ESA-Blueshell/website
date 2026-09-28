package net.blueshell.api.sync.domain

import net.blueshell.api.event.api.EventService
import net.blueshell.api.jobs.api.AbstractJsonJobHandler
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.sync.api.ExternalIdMappingService
import net.blueshell.api.sync.api.ExternalIdMappingService.Companion.EVENT_AGGREGATE
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Instant

/**
 * Queues a calendar sync for every event that has not ended and has no mapping yet, which is an
 * event whose sync never ran. The change's own job covers the rest; this catches one lost with a
 * failed enqueue. An event that has ended is left off, so a sweep never fills the calendar's past.
 */
@Component
class SyncUnsyncedEventsJob(
    objectMapper: ObjectMapper,
    private val events: EventService,
    private val mappings: ExternalIdMappingService,
    private val jobs: JobQueue,
) : AbstractJsonJobHandler<CalendarJobs.SyncUnsyncedEventsPayload>(
        objectMapper,
        CalendarJobs.SyncUnsyncedEvents,
    ) {
    // Settable for tests only.
    internal var clock: Clock = Clock.systemUTC()

    override fun handlePayload(payload: CalendarJobs.SyncUnsyncedEventsPayload) {
        val current = events.idsEndingFrom(Instant.now(clock))
        val synced =
            mappings
                .findBatch(EVENT_AGGREGATE, current, TargetSystem.GOOGLE_CALENDAR.name)
                .map { it.aggregateId }
                .toSet()
        val unsynced = current.filterNot { it in synced }
        log.info("Queueing a calendar sync for {} events whose sync never ran", unsynced.size)
        unsynced.forEach {
            jobs.runAsync(
                CalendarJobs.SyncCalendarEvent,
                CalendarJobs.SyncCalendarEventPayload(it),
                JobTrigger.ANOTHER_JOB,
            )
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SyncUnsyncedEventsJob::class.java)
    }
}
