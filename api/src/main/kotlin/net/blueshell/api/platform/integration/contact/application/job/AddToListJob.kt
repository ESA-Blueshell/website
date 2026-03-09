package net.blueshell.api.platform.integration.contact.application.job

import net.blueshell.api.platform.integration.contact.adapter.ListAdapter
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactListRepository
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactRepository
import net.blueshell.api.platform.integration.queue.ListJobHandler
import net.blueshell.api.shared.enums.ContactSystem
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.NonRetryableJobException
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

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
    adapters: List<ListAdapter>,
    private val contactRepository: ContactRepository,
    private val contactListRepository: ContactListRepository,
) : ListJobHandler<ContactJobs.AddToListPayload>(
    objectMapper,
    ContactJobs.AddToList.payloadType,
    adapters,
) {
    override val jobType: String = ContactJobs.AddToList.type

    override fun systemFrom(payload: ContactJobs.AddToListPayload): ContactSystem = payload.system

    override fun handleForSystem(payload: ContactJobs.AddToListPayload, adapter: ListAdapter) {
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
}

/** Retryable exception: contact not yet visible in the external system. */
private class RetryableAddToListException(message: String) : RuntimeException(message)
