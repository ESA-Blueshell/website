package net.blueshell.api.sync.domain

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class SyncAllContactsJobTest {

    private val objectMapper = ObjectMapper()
    private val userService: UserService = mock()
    private val jobs: TrackedJobDispatcher = mock()
    private val job = SyncAllContactsJob(objectMapper, userService, jobs)

    private fun userWithId(id: Long): User = mock<User>().also {
        whenever(it.id).thenReturn(id)
    }

    @Test
    fun `enqueues one SyncContact job per user`() {
        val users = mutableListOf(userWithId(1L), userWithId(2L))
        whenever(userService.findAll()).thenReturn(users)

        job.handle(objectMapper.writeValueAsString(ContactJobs.SyncAllContactsPayload()))

        verify(jobs).runAsync(eq(ContactJobs.SyncContact), eq(ContactJobs.SyncContactPayload(1L)))
        verify(jobs).runAsync(eq(ContactJobs.SyncContact), eq(ContactJobs.SyncContactPayload(2L)))
    }

    @Test
    fun `does nothing when no users exist`() {
        whenever(userService.findAll()).thenReturn(mutableListOf())

        job.handle(objectMapper.writeValueAsString(ContactJobs.SyncAllContactsPayload()))

        verifyNoInteractions(jobs)
    }

    @Test
    fun `continues enqueueing when one enqueue fails`() {
        val users = mutableListOf(userWithId(1L), userWithId(2L))
        whenever(userService.findAll()).thenReturn(users)
        doThrow(RuntimeException("enqueue boom")).whenever(jobs)
            .runAsync(eq(ContactJobs.SyncContact), eq(ContactJobs.SyncContactPayload(1L)))

        job.handle(objectMapper.writeValueAsString(ContactJobs.SyncAllContactsPayload()))

        verify(jobs, times(1)).runAsync(eq(ContactJobs.SyncContact), eq(ContactJobs.SyncContactPayload(1L)))
        verify(jobs, times(1)).runAsync(eq(ContactJobs.SyncContact), eq(ContactJobs.SyncContactPayload(2L)))
    }
}
