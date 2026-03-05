package net.blueshell.api.platform.integration.contact.job

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.platform.integration.contact.application.ContactSyncService
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.shared.job.ContactJobs
import org.springframework.stereotype.Component

/**
 * Job handler for deleting a user's contact from all registered external systems.
 *
 * Delegates entirely to [ContactSyncService] which resolves system-specific IDs from
 * the stored Contact children.
 */
@Component
class DeleteContactJob(
    objectMapper: ObjectMapper,
    private val contactSyncService: ContactSyncService,
) : AbstractJsonJobHandler<ContactJobs.DeleteContactPayload>(objectMapper, ContactJobs.DeleteContact.payloadType) {
    override val jobType: String = ContactJobs.DeleteContact.type

    override fun handlePayload(payload: ContactJobs.DeleteContactPayload) {
        contactSyncService.deleteContact(payload.userId)
    }
}
