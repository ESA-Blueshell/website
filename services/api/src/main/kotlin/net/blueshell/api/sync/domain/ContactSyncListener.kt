package net.blueshell.api.sync.domain

import net.blueshell.api.contact.api.ContactJobs
import net.blueshell.api.shared.event.AfterCommitListener
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.user.api.UserCreated
import net.blueshell.api.user.api.UserDeleted
import net.blueshell.api.user.api.UserUpdated
import org.springframework.stereotype.Component

/**
 * Fans user lifecycle events out as queued per-user contact sync jobs.
 *
 * Enqueued rather than pushed inline, so the external call sits outside the listener's
 * transaction: each user-change gets its own JobExecution row, and the queue's backoff retries
 * a transient failure without re-running the user-side transaction.
 */
@Component
class ContactSyncListener(
    private val jobs: JobQueue,
) {
    @AfterCommitListener
    fun on(event: UserCreated) {
        jobs.runAsync(ContactJobs.SyncContact, ContactJobs.SyncContactPayload(event.userId), JobTrigger.USER_CHANGED)
    }

    @AfterCommitListener
    fun on(event: UserUpdated) {
        jobs.runAsync(ContactJobs.SyncContact, ContactJobs.SyncContactPayload(event.userId), JobTrigger.USER_CHANGED)
    }

    @AfterCommitListener
    fun on(event: UserDeleted) {
        jobs.runAsync(ContactJobs.RemoveContact, ContactJobs.RemoveContactPayload(event.userId), JobTrigger.USER_REMOVED)
    }
}
