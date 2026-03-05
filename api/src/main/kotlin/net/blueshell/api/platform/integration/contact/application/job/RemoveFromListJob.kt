package net.blueshell.api.platform.integration.contact.application.job

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.domain.user.application.contact.ListSyncAdapter
import net.blueshell.api.platform.integration.contact.application.externalId
import net.blueshell.api.platform.integration.contact.application.externalListId
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactListRepository
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactRepository
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.shared.job.ContactJobs
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Removes a contact from a list in a single external system.
 *
 * Gracefully skips if the contact or list was never synced to the system
 * (no external IDs means the contact was never added, so removal is a no-op).
 */
@Component
class RemoveFromListJob(
    objectMapper: ObjectMapper,
    adapters: List<ListSyncAdapter>,
    private val contactRepository: ContactRepository,
    private val contactListRepository: ContactListRepository,
) : AbstractJsonJobHandler<ContactJobs.RemoveFromListPayload>(
    objectMapper,
    ContactJobs.RemoveFromList.payloadType
) {
    override val jobType: String = ContactJobs.RemoveFromList.type

    private val bySystem = adapters.associateBy { it.system }

    override fun handlePayload(payload: ContactJobs.RemoveFromListPayload) {
        val adapter = bySystem[payload.system]
        if (adapter == null) {
            log.warn("No adapter registered for system {} — skipping removeFromList for user {}", payload.system, payload.userId)
            return
        }

        val contact = contactRepository.findByUserId(payload.userId)
        val externalId = contact?.externalId(payload.system)
        if (externalId == null) {
            log.debug("No {} external ID for user {} — contact was never in this system, skipping removal", payload.system, payload.userId)
            return
        }

        val list = contactListRepository.findById(payload.contactListId).orElse(null)
        val externalListId = list?.externalListId(payload.system)
        if (externalListId == null) {
            log.debug("List {} has no {} external ID — list was never created in this system, skipping removal", payload.contactListId, payload.system)
            return
        }

        adapter.removeFromList(externalId, externalListId)
    }

    companion object {
        private val log = LoggerFactory.getLogger(RemoveFromListJob::class.java)
    }
}
