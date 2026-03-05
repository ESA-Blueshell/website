package net.blueshell.api.platform.integration.contact

import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

/**
 * Unit tests for [ContactSyncScheduler].
 *
 * No Spring context — instantiate directly with mocks.
 */
class ContactSyncSchedulerTest {

    private val userService: UserService = mock()
    private val jobs: TrackedJobDispatcher = mock()
    private val scheduler = ContactSyncScheduler(userService, jobs)

    private fun userWithId(id: Long): User = mock<User>().also {
        whenever(it.id).thenReturn(id)
    }

    @Test
    fun `enqueues one sync job per user`() {
        val users = listOf(userWithId(1L), userWithId(2L), userWithId(3L))
        whenever(userService.findAll()).thenReturn(users.toMutableList())

        scheduler.syncAllContacts()

        verify(jobs, times(3)).enqueue(eq(ContactJobs.SyncContact), any())
    }

    @Test
    fun `does nothing when no users exist`() {
        whenever(userService.findAll()).thenReturn(mutableListOf())

        scheduler.syncAllContacts()

        verifyNoInteractions(jobs)
    }

    @Test
    fun `continues syncing remaining users when one enqueue fails`() {
        val users = listOf(userWithId(1L), userWithId(2L), userWithId(3L))
        whenever(userService.findAll()).thenReturn(users.toMutableList())

        // user 2 (id=2) causes an exception on enqueue
        doThrow(RuntimeException("queue unavailable"))
            .whenever(jobs).enqueue(
                eq(ContactJobs.SyncContact),
                eq(ContactJobs.SyncContactPayload(userId = 2L)),
            )

        scheduler.syncAllContacts()

        // All three users were attempted
        verify(jobs, times(3)).enqueue(eq(ContactJobs.SyncContact), any())
    }
}
