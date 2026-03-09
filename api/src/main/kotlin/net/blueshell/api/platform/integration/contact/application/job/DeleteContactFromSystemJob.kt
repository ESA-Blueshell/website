package net.blueshell.api.platform.integration.contact.application.job

import net.blueshell.api.platform.integration.contact.adapter.ContactAdapter
import net.blueshell.api.platform.integration.queue.ContactJobHandler
import net.blueshell.api.shared.enums.ContactSystem
import net.blueshell.api.shared.job.ContactJobs
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Routes a delete operation to the correct [ContactSystemAdapter] for a single system.
 *
 * The external ID is captured at dispatch time by [ContactSyncService.deleteContact] before the
 * Contact record is removed from the database.
 */
@Component
class DeleteContactFromSystemJob(
    objectMapper: ObjectMapper,
    adapters: List<ContactAdapter>,
) : ContactJobHandler<ContactJobs.DeleteContactFromSystemPayload>(
    objectMapper,
    ContactJobs.DeleteContactFromSystem.payloadType,
    adapters,
) {
    override val jobType: String = ContactJobs.DeleteContactFromSystem.type

    override fun systemFrom(payload: ContactJobs.DeleteContactFromSystemPayload): ContactSystem = payload.system

    override fun handleForSystem(
        payload: ContactJobs.DeleteContactFromSystemPayload,
        adapter: ContactAdapter,
    ) {
        adapter.deleteContact(payload.externalId)
    }
}
