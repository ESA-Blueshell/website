package net.blueshell.api.platform.integration.sync.listener

import net.blueshell.api.domain.user.application.event.UserCreated
import net.blueshell.api.domain.user.application.event.UserDeleted
import net.blueshell.api.domain.user.application.event.UserUpdated
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Modulith listener that fans user lifecycle events out as queued
 * per-user contact sync jobs.
 *
 * The listener used to call [ContactSyncService.sync] / `.remove` inline,
 * which meant the Brevo HTTP push happened inside the listener's
 * transaction and had no retry visibility. Enqueueing the work as a
 * [ContactJobs.SyncContact] / [ContactJobs.RemoveContact] job keeps the
 * listener cheap, gives each user-change its own JobExecution row, and
 * lets the queue's exponential backoff retry transient external
 * failures without re-running the user-side transaction.
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
