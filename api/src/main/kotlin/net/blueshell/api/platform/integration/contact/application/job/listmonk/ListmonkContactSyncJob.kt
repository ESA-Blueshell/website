package net.blueshell.api.platform.integration.contact.application.job.listmonk

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.platform.integration.contact.adapter.listmonk.ListmonkContactAdapter
import net.blueshell.api.platform.integration.contact.adapter.toContactData
import net.blueshell.api.platform.integration.contact.persistence.Contact
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactRepository
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.shared.enums.ContactSystem
import net.blueshell.api.shared.job.ContactIntegrationJobProvider
import net.blueshell.api.shared.job.JobDefinition
import net.blueshell.api.shared.job.ListmonkJobs
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Handles create/update/delete of a Listmonk subscriber for a single user.
 *
 * Reads entity state from [ContactRepository] to determine the operation:
 * - `deletedAt` set + external ID exists → delete from Listmonk, clear external ID
 * - active + no external ID → create in Listmonk, store external ID
 * - active + external ID exists → update in Listmonk
 *
 * Also implements [ContactIntegrationJobProvider] so event listeners and
 * [SpawnContactSyncsJob] can dispatch Listmonk-specific jobs without coupling.
 */
@Component
@Profile("!test")
class ListmonkContactSyncJob(
    objectMapper: ObjectMapper,
    private val adapter: ListmonkContactAdapter,
    private val contactRepository: ContactRepository,
    private val userService: UserService,
) : AbstractJsonJobHandler<ListmonkJobs.ListmonkContactSyncPayload>(
    objectMapper,
    ListmonkJobs.SyncContact.payloadType,
), ContactIntegrationJobProvider {

    override val jobType: String = ListmonkJobs.SyncContact.type
    override val system: ContactSystem = ContactSystem.LISTMONK

    override fun handlePayload(payload: ListmonkJobs.ListmonkContactSyncPayload) {
        val userId = payload.userId

        val contact = contactRepository.findByUserIdIncludingDeleted(userId)

        if (contact != null && contact.isSoftDeleted) {
            // Soft-deleted: remove from Listmonk and clear stored external ID
            val externalId = contact.externalId(ContactSystem.LISTMONK)
            if (externalId == null) {
                log.debug("Contact for user {} is deleted but has no Listmonk ID — nothing to delete", userId)
                return
            }
            adapter.deleteContact(externalId)
            contact.clearExternalId(ContactSystem.LISTMONK)
            contactRepository.save(contact)
            log.info("Deleted Listmonk subscriber for user {}", userId)
            return
        }

        // Active contact: create or update
        val record = contact ?: contactRepository.save(Contact(userId = userId))
        val user = userService.findById(userId)
        val data = user.toContactData()

        val existingId = record.externalId(ContactSystem.LISTMONK)
        if (existingId == null) {
            val newId = adapter.createContact(data)
            record.setExternalId(ContactSystem.LISTMONK, newId)
            record.updateSnapshot(data.email, data.firstName, data.lastName, data.phoneNumber, data.newsletter, data.isMember)
            contactRepository.save(record)
            log.info("Created Listmonk subscriber for user {}", userId)
        } else {
            adapter.updateContact(existingId, data)
            record.updateSnapshot(data.email, data.firstName, data.lastName, data.phoneNumber, data.newsletter, data.isMember)
            contactRepository.save(record)
            log.debug("Updated Listmonk subscriber for user {}", userId)
        }
    }

    override fun contactSyncJob(userId: Long): Pair<JobDefinition<*>, Any> =
        Pair(ListmonkJobs.SyncContact, ListmonkJobs.ListmonkContactSyncPayload(userId))

    override fun listSyncJob(userId: Long, contactListId: Long): Pair<JobDefinition<*>, Any> =
        Pair(ListmonkJobs.SyncListMembership, ListmonkJobs.ListmonkListSyncPayload(userId, contactListId))

    companion object {
        private val log = LoggerFactory.getLogger(ListmonkContactSyncJob::class.java)
    }
}
