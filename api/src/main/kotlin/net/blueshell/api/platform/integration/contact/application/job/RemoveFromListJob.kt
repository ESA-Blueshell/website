package net.blueshell.api.platform.integration.contact.application.job

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.domain.user.application.contact.ContactSystem
import net.blueshell.api.domain.user.application.contact.ContactSystemAdapter
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactListRepository
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactRepository
import net.blueshell.api.platform.integration.queue.PerSystemJobHandler
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
    adapters: List<ContactSystemAdapter>,
    private val contactRepository: ContactRepository,
    private val contactListRepository: ContactListRepository,
) : PerSystemJobHandler<ContactJobs.RemoveFromListPayload>(
    objectMapper,
    ContactJobs.RemoveFromList.payloadType,
    adapters,
) {
    override val jobType: String = ContactJobs.RemoveFromList.type

    override fun systemFrom(payload: ContactJobs.RemoveFromListPayload): ContactSystem = payload.system

    override fun handleForSystem(payload: ContactJobs.RemoveFromListPayload, adapter: ContactSystemAdapter) {
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
