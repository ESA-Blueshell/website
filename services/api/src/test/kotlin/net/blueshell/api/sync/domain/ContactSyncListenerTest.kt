package net.blueshell.api.sync.domain

import net.blueshell.api.domain.user.application.event.UserCreated
import net.blueshell.api.domain.user.application.event.UserDeleted
import net.blueshell.api.domain.user.application.event.UserUpdated
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

class ContactSyncListenerTest {

    private val jobs: TrackedJobDispatcher = mock()
    private val listener = ContactSyncListener(jobs)

    @Test
    fun `UserCreated enqueues a SyncContact job`() {
        listener.on(UserCreated(7L))

        verify(jobs).runAsync(eq(ContactJobs.SyncContact), eq(ContactJobs.SyncContactPayload(7L)))
        verifyNoMoreInteractions(jobs)
    }

    @Test
    fun `UserUpdated enqueues a SyncContact job`() {
        listener.on(UserUpdated(11L))

        verify(jobs).runAsync(eq(ContactJobs.SyncContact), eq(ContactJobs.SyncContactPayload(11L)))
        verifyNoMoreInteractions(jobs)
    }

    @Test
    fun `UserDeleted enqueues a RemoveContact job`() {
        listener.on(UserDeleted(13L))

        verify(jobs).runAsync(eq(ContactJobs.RemoveContact), eq(ContactJobs.RemoveContactPayload(13L)))
        verifyNoMoreInteractions(jobs)
    }
}
