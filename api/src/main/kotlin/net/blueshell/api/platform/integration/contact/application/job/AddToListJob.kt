package net.blueshell.api.platform.integration.contact.application.job

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.domain.user.application.contact.ListSyncAdapter
import net.blueshell.api.platform.integration.contact.application.externalId
import net.blueshell.api.platform.integration.contact.application.externalListId
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactListRepository
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactRepository
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.NonRetryableJobException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Adds a contact to a list in a single external system.
 *
 * Retries if the contact has not yet been synced to the external system (externalId not set).
 * Fails permanently (non-retryable) if the list itself has no external ID for the system,
 * since that indicates a misconfiguration rather than a timing issue.
 */
@Component
class AddToListJob(
    objectMapper: ObjectMapper,
    adapters: List<ListSyncAdapter>,
    private val contactRepository: ContactRepository,
    private val contactListRepository: ContactListRepository,
) : AbstractJsonJobHandler<ContactJobs.AddToListPayload>(
    objectMapper,
    ContactJobs.AddToList.payloadType
) {
    override val jobType: String = ContactJobs.AddToList.type

    private val bySystem = adapters.associateBy { it.system }

    override fun handlePayload(payload: ContactJobs.AddToListPayload) {
        val adapter = bySystem[payload.system]
        if (adapter == null) {
            log.warn("No adapter registered for system {} — skipping addToList for user {}", payload.system, payload.userId)
            return
        }

        val contact = contactRepository.findByUserId(payload.userId)
        val externalId = contact?.externalId(payload.system)
            ?: throw RetryableAddToListException(
                "Contact not yet synced to ${payload.system} for user ${payload.userId} — will retry"
            )

        val list = contactListRepository.findById(payload.contactListId).orElse(null)
        val externalListId = list?.externalListId(payload.system)
            ?: throw NonRetryableJobException(
                "List ${payload.contactListId} has no external ID for ${payload.system} — cannot add contact"
            )

        adapter.addToList(externalId, externalListId)
    }

    companion object {
        private val log = LoggerFactory.getLogger(AddToListJob::class.java)
    }
}

/** Retryable exception: contact not yet visible in the external system. */
private class RetryableAddToListException(message: String) : RuntimeException(message)
