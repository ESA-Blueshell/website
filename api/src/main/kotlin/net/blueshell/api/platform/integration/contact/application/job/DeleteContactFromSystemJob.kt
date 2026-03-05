package net.blueshell.api.platform.integration.contact.application.job

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.domain.user.application.contact.ContactSyncAdapter
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.shared.job.ContactJobs
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Routes a delete operation to the correct [ContactSyncAdapter] for a single system.
 *
 * The external ID is captured at dispatch time by [ContactSyncService.deleteContact] before the
 * Contact record is removed from the database.
 */
@Component
class DeleteContactFromSystemJob(
    objectMapper: ObjectMapper,
    adapters: List<ContactSyncAdapter>,
) : AbstractJsonJobHandler<ContactJobs.DeleteContactFromSystemPayload>(
    objectMapper,
    ContactJobs.DeleteContactFromSystem.payloadType
) {
    override val jobType: String = ContactJobs.DeleteContactFromSystem.type

    private val bySystem = adapters.associateBy { it.system }

    override fun handlePayload(payload: ContactJobs.DeleteContactFromSystemPayload) {
        val adapter = bySystem[payload.system]
        if (adapter == null) {
            log.warn("No adapter registered for system {} — skipping delete of externalId {}", payload.system, payload.externalId)
            return
        }
        adapter.deleteContact(payload.externalId)
    }

    companion object {
        private val log = LoggerFactory.getLogger(DeleteContactFromSystemJob::class.java)
    }
}
