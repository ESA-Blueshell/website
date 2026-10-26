package net.blueshell.api.sync.domain

import net.blueshell.api.contact.api.ContactJobs
import net.blueshell.api.jobs.api.JobOutcome
import net.blueshell.api.shared.job.JobCount
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTally
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.testsupport.Entities
import net.blueshell.api.testsupport.runJob
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import tools.jackson.databind.ObjectMapper

class SyncAllContactsJobTest {
    private val objectMapper = ObjectMapper()
    private val userService: UserService = mock()
    private val jobs: JobQueue = mock()
    private val job = SyncAllContactsJob(objectMapper, userService, jobs)

    private fun userWithId(id: Long): User =
        Entities.user().also {
            it.id = id
        }

    @Test
    fun `enqueues one SyncContact job per user`() {
        val users = mutableListOf(userWithId(1L), userWithId(2L))
        whenever(userService.findAll()).thenReturn(users)

        val outcome = job.runJob(objectMapper.writeValueAsString(ContactJobs.SyncAllContactsPayload())) as JobOutcome.Done

        assertThat(outcome.counts).containsExactly(JobCount(JobTally.USERS_QUEUED, 2))
        verify(jobs).runAsync(eq(ContactJobs.SyncContact), eq(ContactJobs.SyncContactPayload(1L)), eq(JobTrigger.ANOTHER_JOB), anyOrNull())
        verify(jobs).runAsync(eq(ContactJobs.SyncContact), eq(ContactJobs.SyncContactPayload(2L)), eq(JobTrigger.ANOTHER_JOB), anyOrNull())
    }

    @Test
    fun `does nothing when no users exist`() {
        whenever(userService.findAll()).thenReturn(mutableListOf())

        job.runJob(objectMapper.writeValueAsString(ContactJobs.SyncAllContactsPayload()))

        verifyNoInteractions(jobs)
    }

    @Test
    fun `continues enqueueing when one enqueue fails`() {
        val users = mutableListOf(userWithId(1L), userWithId(2L))
        whenever(userService.findAll()).thenReturn(users)
        doThrow(RuntimeException("enqueue boom"))
            .whenever(jobs)
            .runAsync(eq(ContactJobs.SyncContact), eq(ContactJobs.SyncContactPayload(1L)), eq(JobTrigger.ANOTHER_JOB), anyOrNull())

        val outcome = job.runJob(objectMapper.writeValueAsString(ContactJobs.SyncAllContactsPayload())) as JobOutcome.Done

        assertThat(outcome.counts).containsExactly(JobCount(JobTally.USERS_QUEUED, 1))

        verify(
            jobs,
            times(1),
        ).runAsync(eq(ContactJobs.SyncContact), eq(ContactJobs.SyncContactPayload(1L)), eq(JobTrigger.ANOTHER_JOB), anyOrNull())
        verify(
            jobs,
            times(1),
        ).runAsync(eq(ContactJobs.SyncContact), eq(ContactJobs.SyncContactPayload(2L)), eq(JobTrigger.ANOTHER_JOB), anyOrNull())
    }
}
