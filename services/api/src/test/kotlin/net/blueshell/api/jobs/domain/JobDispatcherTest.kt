package net.blueshell.api.jobs.domain

import net.blueshell.api.jobs.api.Enqueued
import net.blueshell.api.jobs.api.JobExecutionService
import net.blueshell.api.jobs.api.JobExecutor
import net.blueshell.api.jobs.persistence.JobExecution
import net.blueshell.api.platform.config.JobQueueProperties
import net.blueshell.api.shared.job.JobQueued
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.shared.tracking.ActorProvider
import net.blueshell.api.sync.domain.CalendarJobs
import net.blueshell.api.sync.domain.DiscordPostJobs
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import tools.jackson.databind.json.JsonMapper

class JobDispatcherTest {
    private val executions: JobExecutionService = mock()
    private val dispatcher =
        JobDispatcher(JsonMapper.builder().build(), executions, mock(), mock(), JobQueueProperties(autoDispatch = false), mock())

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

    @Test
    fun `dispatches a new row and a twin pulled forward, and leaves a twin that is about to run`() {
        val executor: JobExecutor = mock()
        val live =
            JobDispatcher(
                JsonMapper.builder().build(),
                executions,
                mock(),
                executor,
                JobQueueProperties(autoDispatch = true),
                mock(),
            )
        val fresh = JobExecution(jobType = "discord.announcement").apply { id = 7 }
        val due = JobExecution(jobType = "discord.post").apply { id = 8 }
        whenever(executions.createQueued(eq("discord.announcement"), any(), any(), anyOrNull(), anyOrNull(), any(), any()))
            .thenReturn(Enqueued(fresh, dispatch = true))
        whenever(executions.createQueued(eq("discord.post"), any(), any(), anyOrNull(), anyOrNull(), any(), any()))
            .thenReturn(Enqueued(due, dispatch = false))

        assertThat(
            live.runAsync(DiscordPostJobs.Announcement, DiscordPostJobs.EventPostPayload(4), JobTrigger.EVENT_UPDATED, Actor.system()),
        ).isSameAs(fresh)
        assertThat(
            live.runAsync(DiscordPostJobs.CalendarPost, DiscordPostJobs.EventPostPayload(4), JobTrigger.EVENT_UPDATED, Actor.system()),
        ).isSameAs(due)

        verify(executor).executeAsync(7)
        verify(executor, never()).executeAsync(8)
    }

    @Test
    fun `tells who listens that a job was queued, and queues what an execution ran as a new one`() {
        val events: ApplicationEventPublisher = mock()
        val actors: ActorProvider = mock { on { currentOrSystem() } doReturn Actor.system() }
        val queue =
            JobDispatcher(JsonMapper.builder().build(), executions, actors, mock(), JobQueueProperties(autoDispatch = false), events)
        val before = JobExecution(jobType = "auth.recovery", payload = """{"userId":3}""").apply { id = 4 }
        val again = JobExecution(jobType = "auth.recovery").apply { id = 9 }
        whenever(executions.findById(4)).thenReturn(before)
        whenever(executions.createQueued(eq("auth.recovery"), any(), any(), anyOrNull(), anyOrNull(), any(), any()))
            .thenReturn(Enqueued(again, dispatch = true))

        assertThat(queue.runAgain(4, JobTrigger.SITE_ACTION)).isSameAs(again)

        verify(executions)
            .createQueued(eq("auth.recovery"), eq("""{"userId":3}"""), any(), eq(JobTrigger.SITE_ACTION), anyOrNull(), eq(false), eq(false))
        verify(events).publishEvent(JobQueued(9, "auth.recovery", """{"userId":3}""", Actor.system()))
    }
}
