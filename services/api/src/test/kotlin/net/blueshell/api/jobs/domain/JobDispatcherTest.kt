package net.blueshell.api.jobs.domain

import net.blueshell.api.jobs.api.JobExecutionService
import net.blueshell.api.platform.config.JobQueueProperties
import net.blueshell.api.shared.job.CalendarJobs
import net.blueshell.api.shared.job.DiscordPostJobs
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.shared.tracking.Actor
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import tools.jackson.databind.json.JsonMapper

class JobDispatcherTest {
    private val executions: JobExecutionService = mock()
    private val dispatcher =
        JobDispatcher(JsonMapper.builder().build(), executions, mock(), mock(), JobQueueProperties(autoDispatch = false))

    @Test
    fun `records what queued a job, and keeps it behind a running twin only where its definition asks for it`() {
        dispatcher.runAsync(
            CalendarJobs.SyncCalendarEvent,
            CalendarJobs.SyncCalendarEventPayload(4),
            JobTrigger.EVENT_UPDATED,
            Actor.system(),
        )
        dispatcher.runAsync(DiscordPostJobs.Announcement, DiscordPostJobs.EventPostPayload(4), JobTrigger.MORNING_RUN, Actor.system())

        verify(
            executions,
        ).createQueued(eq("calendar.sync-event"), any(), any(), eq(JobTrigger.EVENT_UPDATED), anyOrNull(), eq(false), eq(false))
        verify(
            executions,
        ).createQueued(eq("discord.announcement"), any(), any(), eq(JobTrigger.MORNING_RUN), anyOrNull(), eq(true), eq(false))
    }
}
