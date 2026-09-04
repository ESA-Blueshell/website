package net.blueshell.api.sync.domain

import net.blueshell.api.user.api.UserCreated
import net.blueshell.api.user.api.UserDeleted
import net.blueshell.api.user.api.UserUpdated
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Modulith listener that fans user lifecycle events out as queued per-user contact sync jobs.
 *
 * Enqueued rather than pushed inline, so the external call sits outside the listener's
 * transaction: each user-change gets its own JobExecution row, and the queue's backoff retries
 * a transient failure without re-running the user-side transaction.
 */
@Component
class ContactSyncListener(
    private val jobs: TrackedJobDispatcher,
) {
    @ApplicationModuleListener
    fun on(event: UserCreated) {
        jobs.runAsync(ContactJobs.SyncContact, ContactJobs.SyncContactPayload(event.userId))
    }

    @ApplicationModuleListener
    fun on(event: UserUpdated) {
        jobs.runAsync(ContactJobs.SyncContact, ContactJobs.SyncContactPayload(event.userId))
    }

    @ApplicationModuleListener
    fun on(event: UserDeleted) {
        jobs.runAsync(ContactJobs.RemoveContact, ContactJobs.RemoveContactPayload(event.userId))
    }
}
