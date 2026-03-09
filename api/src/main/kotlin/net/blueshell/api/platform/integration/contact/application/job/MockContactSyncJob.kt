package net.blueshell.api.platform.integration.contact.application.job

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.platform.integration.contact.adapter.toContactData
import net.blueshell.api.platform.integration.contact.persistence.Contact
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactRepository
import net.blueshell.api.platform.integration.mock.MockContactAdapter
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.shared.enums.ContactSystem
import net.blueshell.api.shared.job.ListmonkJobs
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Mock contact sync job for test and dev profiles.
 *
 * Handles [ListmonkJobs.SyncContact] using [MockContactAdapter],
 * replicating the behaviour of [ListmonkContactSyncJob] in non-test profiles.
 */
@Component
@Profile("test | dev")
class MockContactSyncJob(
    objectMapper: ObjectMapper,
    private val adapter: MockContactAdapter,
    private val contactRepository: ContactRepository,
    private val userService: UserService,
) : AbstractJsonJobHandler<ListmonkJobs.ListmonkContactSyncPayload>(
    objectMapper,
    ListmonkJobs.SyncContact.payloadType,
) {
    override val jobType: String = ListmonkJobs.SyncContact.type

    override fun handlePayload(payload: ListmonkJobs.ListmonkContactSyncPayload) {
        val userId = payload.userId

        val contact = contactRepository.findByUserIdIncludingDeleted(userId)

        if (contact != null && contact.isSoftDeleted) {
            val externalId = contact.externalId(ContactSystem.LISTMONK)
            if (externalId != null) {
                adapter.deleteContact(externalId)
                contact.clearExternalId(ContactSystem.LISTMONK)
                contactRepository.save(contact)
                log.info("Mock: Deleted Listmonk contact for user {}", userId)
            }
            return
        }

        val record = contact ?: contactRepository.save(Contact(userId = userId))
        val user = userService.findById(userId)
        val data = user.toContactData()

        val existingId = record.externalId(ContactSystem.LISTMONK)
        if (existingId == null) {
            val newId = adapter.createContact(data)
            record.setExternalId(ContactSystem.LISTMONK, newId)
            record.updateSnapshot(data.email, data.firstName, data.lastName, data.phoneNumber, data.newsletter, data.isMember)
            contactRepository.save(record)
        } else {
            adapter.updateContact(existingId, data)
            record.updateSnapshot(data.email, data.firstName, data.lastName, data.phoneNumber, data.newsletter, data.isMember)
            contactRepository.save(record)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(MockContactSyncJob::class.java)
    }
}
