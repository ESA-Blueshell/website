package net.blueshell.api.platform.integration.contact.application.job

import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.platform.integration.sync.application.ContactSyncService
import net.blueshell.api.shared.job.ContactJobs
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Per-user contact sync: pushes one user's current contact state to every
 * registered contact target.
 *
 * Enqueued by [SyncAllContactsJob] (daily fan-out) and could also be
 * fired ad-hoc. Each invocation is its own JobExecution row with its own
 * retry schedule, so per-user failures stay observable in the Job Manager
 * instead of being swallowed inside a batch handler.
 */
@Component
class SyncContactJob(
    objectMapper: ObjectMapper,
    private val contactSync: ContactSyncService,
) : AbstractJsonJobHandler<ContactJobs.SyncContactPayload>(
    objectMapper,
    ContactJobs.SyncContact.payloadType,
) {
    override val jobType: String = ContactJobs.SyncContact.type

    override fun handlePayload(payload: ContactJobs.SyncContactPayload) {
        contactSync.sync(payload.userId)
    }
}
