package net.blueshell.api.platform.integration.contact.job

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.platform.integration.contact.application.ContactSyncService
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.shared.job.ContactJobs
import org.springframework.stereotype.Component

/**
 * Job handler for syncing user contacts with all registered external systems.
 *
 * Delegates entirely to [ContactSyncService] which handles delta detection,
 * create vs update routing, and per-adapter fault tolerance.
 */
@Component
class SyncContactJob(
    objectMapper: ObjectMapper,
    private val contactSyncService: ContactSyncService,
) : AbstractJsonJobHandler<ContactJobs.SyncContactPayload>(objectMapper, ContactJobs.SyncContact.payloadType) {
    override val jobType: String = ContactJobs.SyncContact.type

    override fun handlePayload(payload: ContactJobs.SyncContactPayload) {
        contactSyncService.syncContact(payload.userId)
    }
}
