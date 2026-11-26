package net.blueshell.api.jobs.domain

import net.blueshell.api.jobs.api.Enqueued
import net.blueshell.api.jobs.api.JobExecutionService
import net.blueshell.api.jobs.api.JobExecutor
import net.blueshell.api.jobs.persistence.JobExecution
import net.blueshell.api.platform.config.JobQueueProperties
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.sync.domain.CalendarJobs
import net.blueshell.api.sync.domain.DiscordPostJobs
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.core.task.TaskRejectedException
import org.springframework.transaction.support.TransactionSynchronizationManager
import tools.jackson.databind.json.JsonMapper

class JobDispatcherTest {
    private val executions: JobExecutionService = mock()
    private val dispatcher =
        JobDispatcher(JsonMapper.builder().build(), executions, mock(), mock(), mock(), JobQueueProperties(autoDispatch = false))

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
                mock(),
                JobQueueProperties(autoDispatch = true),
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
    fun `a rejected after-commit dispatch does not stop the other callbacks`() {
        val executor: JobExecutor = mock()
        val live =
            JobDispatcher(
                JsonMapper.builder().build(),
                executions,
                mock(),
                executor,
                mock(),
                JobQueueProperties(autoDispatch = true),
            )
        val first = JobExecution(jobType = "discord.announcement").apply { id = 7 }
        val second = JobExecution(jobType = "discord.post").apply { id = 8 }
        whenever(executions.createQueued(eq("discord.announcement"), any(), any(), anyOrNull(), anyOrNull(), any(), any()))
            .thenReturn(Enqueued(first, dispatch = true))
        whenever(executions.createQueued(eq("discord.post"), any(), any(), anyOrNull(), anyOrNull(), any(), any()))
            .thenReturn(Enqueued(second, dispatch = true))
        doThrow(TaskRejectedException("Executor full")).whenever(executor).executeAsync(7L)

        TransactionSynchronizationManager.initSynchronization()
        try {
            live.runAsync(DiscordPostJobs.Announcement, DiscordPostJobs.EventPostPayload(4), JobTrigger.EVENT_UPDATED, Actor.system())
            live.runAsync(DiscordPostJobs.CalendarPost, DiscordPostJobs.EventPostPayload(4), JobTrigger.EVENT_UPDATED, Actor.system())
            TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
        }

        verify(executor).executeAsync(7L)
        verify(executor).executeAsync(8L)
    }

    @Test
    fun `a rejected direct dispatch leaves the row queued without failing the caller`() {
        val executor: JobExecutor = mock()
        val live =
            JobDispatcher(
                JsonMapper.builder().build(),
                executions,
                mock(),
                executor,
                mock(),
                JobQueueProperties(autoDispatch = true),
            )
        val fresh = JobExecution(jobType = "discord.announcement").apply { id = 9 }
        whenever(executions.createQueued(eq("discord.announcement"), any(), any(), anyOrNull(), anyOrNull(), any(), any()))
            .thenReturn(Enqueued(fresh, dispatch = true))
        doThrow(TaskRejectedException("Executor full")).whenever(executor).executeAsync(9L)

        val returned =
            live.runAsync(DiscordPostJobs.Announcement, DiscordPostJobs.EventPostPayload(4), JobTrigger.EVENT_UPDATED, Actor.system())

        assertThat(returned).isSameAs(fresh)
        verify(executor).executeAsync(9L)
    }

    @Test
    fun `email jobs nudge the batch worker instead of the general executor`() {
        val executor: JobExecutor = mock()
        val worker: EmailJobWorker = mock()
        val live =
            JobDispatcher(
                JsonMapper.builder().build(),
                executions,
                mock(),
                executor,
                worker,
                JobQueueProperties(autoDispatch = true),
            )
        val email = JobExecution(jobType = "email.test").apply { id = 12 }
        whenever(executions.createQueued(eq("email.test"), any(), any(), anyOrNull(), anyOrNull(), any(), any()))
            .thenReturn(Enqueued(email, dispatch = true))

        live.runAsync("email.test", mapOf("id" to 1), JobTrigger.EVENT_UPDATED, Actor.system())

        verify(worker).nudge()
        verify(executor, never()).executeAsync(any())
    }
}
