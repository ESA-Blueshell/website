package net.blueshell.api.shared.job

import net.blueshell.api.shared.enums.ContactSystem

/**
 * Contract for integration-specific contact job creation.
 *
 * Each external contact integration (Brevo, Listmonk) implements this interface
 * so that event listeners and spawn jobs can dispatch per-integration jobs
 * without knowing the concrete job types.
 */
interface ContactIntegrationJobProvider {
    val system: ContactSystem

    /** Returns an [EnqueueableJob] for syncing a single user's contact record. */
    fun contactSyncJob(userId: Long): EnqueueableJob<*>

    /** Returns an [EnqueueableJob] for syncing a user's list membership. */
    fun listSyncJob(userId: Long, contactListId: Long): EnqueueableJob<*>
}
