package net.blueshell.api.platform.integration.contact.application.job.brevo

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.platform.integration.contact.adapter.brevo.BrevoListAdapter
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactListMembershipRepository
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactListRepository
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactRepository
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.shared.enums.ContactSystem
import net.blueshell.api.shared.job.BrevoJobs
import net.blueshell.api.shared.job.NonRetryableJobException
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Syncs a user's list membership to Brevo.
 *
 * If the user has an active [ContactListMembership] in the DB: adds them to the Brevo list.
 * If no membership exists: removes them (graceful no-op if IDs are missing).
 *
 * Throws a retryable exception if the contact has not yet been synced to Brevo,
 * since list add requires an existing external contact ID.
 */
@Component
@Profile("!test & !dev")
class BrevoListSyncJob(
    objectMapper: ObjectMapper,
    private val adapter: BrevoListAdapter,
    private val contactRepository: ContactRepository,
    private val contactListRepository: ContactListRepository,
    private val contactListMembershipRepository: ContactListMembershipRepository,
) : AbstractJsonJobHandler<BrevoJobs.BrevoListSyncPayload>(
    objectMapper,
    BrevoJobs.SyncListMembership.payloadType,
) {
    override val jobType: String = BrevoJobs.SyncListMembership.type

    override fun handlePayload(payload: BrevoJobs.BrevoListSyncPayload) {
        val contact = contactRepository.findByUserId(payload.userId)
        val externalContactId = contact?.externalId(ContactSystem.BREVO)

        val list = contactListRepository.findById(payload.contactListId).orElse(null)
        val externalListId = list?.externalListId(ContactSystem.BREVO)

        val hasMembership = contact?.id?.let { contactId ->
            contactListMembershipRepository.findByContactIdAndContactListId(contactId, payload.contactListId) != null
        } ?: false

        if (hasMembership) {
            if (externalContactId == null) {
                throw RetryableBrevoListException(
                    "Contact not yet synced to BREVO for user ${payload.userId} — will retry"
                )
            }
            if (externalListId == null) {
                throw NonRetryableJobException(
                    "List ${payload.contactListId} has no Brevo external ID — cannot add contact"
                )
            }
            adapter.addToList(externalContactId, externalListId)
            log.debug("Added user {} to Brevo list {}", payload.userId, payload.contactListId)
        } else {
            if (externalContactId != null && externalListId != null) {
                adapter.removeFromList(externalContactId, externalListId)
                log.debug("Removed user {} from Brevo list {}", payload.userId, payload.contactListId)
            } else {
                log.debug("No Brevo IDs for user {} / list {} — skipping removal", payload.userId, payload.contactListId)
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(BrevoListSyncJob::class.java)
    }
}

private class RetryableBrevoListException(message: String) : RuntimeException(message)
