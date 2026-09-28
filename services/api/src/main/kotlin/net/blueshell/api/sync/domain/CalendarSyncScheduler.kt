package net.blueshell.api.sync.domain

import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** The daily calendar sweep, at 02:30, half an hour after the contact sweep. */
@Component
class CalendarSyncScheduler(
    private val jobs: JobQueue,
) {
    @Scheduled(cron = "\${calendar.sync-cron:0 30 2 * * *}")
    fun syncUnsyncedEvents() {
        jobs.runAsync(CalendarJobs.SyncUnsyncedEvents, CalendarJobs.SyncUnsyncedEventsPayload(), JobTrigger.SCHEDULED_RUN)
    }
}
