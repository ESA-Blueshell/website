package net.blueshell.api.platform.integration.contact.application.job

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactListMembershipRepository
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactListRepository
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactRepository
import net.blueshell.api.platform.integration.mock.MockContactAdapter
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.shared.enums.ContactSystem
import net.blueshell.api.shared.job.ListmonkJobs
import net.blueshell.api.shared.job.NonRetryableJobException
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Mock list sync job for test and dev profiles.
 *
 * Handles [ListmonkJobs.SyncListMembership] using [MockContactAdapter],
 * replicating the behaviour of [ListmonkListSyncJob] in non-test profiles.
 */
@Component
@Profile("test | dev")
class MockListSyncJob(
    objectMapper: ObjectMapper,
    private val adapter: MockContactAdapter,
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
            if (externalContactId == null) throw RetryableMockListException(
                "Contact not yet synced to LISTMONK for user ${payload.userId}"
            )
            if (externalListId == null) throw NonRetryableJobException(
                "List ${payload.contactListId} has no Listmonk external ID"
            )
            adapter.addToList(externalContactId, externalListId)
            log.debug("Mock: Added user {} to list {}", payload.userId, payload.contactListId)
        } else {
            if (externalContactId != null && externalListId != null) {
                adapter.removeFromList(externalContactId, externalListId)
                log.debug("Mock: Removed user {} from list {}", payload.userId, payload.contactListId)
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(MockListSyncJob::class.java)
    }
}

private class RetryableMockListException(message: String) : RuntimeException(message)
