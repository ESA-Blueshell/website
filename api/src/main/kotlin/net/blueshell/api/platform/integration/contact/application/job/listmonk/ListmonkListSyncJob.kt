package net.blueshell.api.platform.integration.contact.application.job.listmonk

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.platform.integration.contact.adapter.listmonk.ListmonkListAdapter
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactListMembershipRepository
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactListRepository
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactRepository
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.shared.enums.ContactSystem
import net.blueshell.api.shared.job.ListmonkJobs
import net.blueshell.api.shared.job.NonRetryableJobException
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Syncs a user's list membership to Listmonk.
 *
 * If the user has an active [ContactListMembership] in the DB: adds them to the Listmonk list.
 * If no membership exists: removes them (graceful no-op if IDs are missing).
 *
 * Throws a retryable exception if the contact has not yet been synced to Listmonk,
 * since list add requires an existing external subscriber ID.
 */
@Component
@Profile("!test")
class ListmonkListSyncJob(
    objectMapper: ObjectMapper,
    private val adapter: ListmonkListAdapter,
    private val contactRepository: ContactRepository,
    private val contactListRepository: ContactListRepository,
    private val contactListMembershipRepository: ContactListMembershipRepository,
) : AbstractJsonJobHandler<ListmonkJobs.ListmonkListSyncPayload>(
    objectMapper,
    ListmonkJobs.SyncListMembership.payloadType,
) {
    override val jobType: String = ListmonkJobs.SyncListMembership.type

    override fun handlePayload(payload: ListmonkJobs.ListmonkListSyncPayload) {
        val contact = contactRepository.findByUserId(payload.userId)
        val externalContactId = contact?.externalId(ContactSystem.LISTMONK)

        val list = contactListRepository.findById(payload.contactListId).orElse(null)
        val externalListId = list?.externalListId(ContactSystem.LISTMONK)

        val hasMembership = contact?.id?.let { contactId ->
            contactListMembershipRepository.findByContactIdAndContactListId(contactId, payload.contactListId) != null
        } ?: false

        if (hasMembership) {
            if (externalContactId == null) {
                throw RetryableListmonkListException(
                    "Contact not yet synced to LISTMONK for user ${payload.userId} — will retry"
                )
            }
            if (externalListId == null) {
                throw NonRetryableJobException(
                    "List ${payload.contactListId} has no Listmonk external ID — cannot add subscriber"
                )
            }
            adapter.addToList(externalContactId, externalListId)
            log.debug("Added user {} to Listmonk list {}", payload.userId, payload.contactListId)
        } else {
            if (externalContactId != null && externalListId != null) {
                adapter.removeFromList(externalContactId, externalListId)
                log.debug("Removed user {} from Listmonk list {}", payload.userId, payload.contactListId)
            } else {
                log.debug("No Listmonk IDs for user {} / list {} — skipping removal", payload.userId, payload.contactListId)
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ListmonkListSyncJob::class.java)
    }
}

private class RetryableListmonkListException(message: String) : RuntimeException(message)
