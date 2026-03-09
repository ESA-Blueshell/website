package net.blueshell.api.platform.integration.contact.application.job.brevo

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.platform.integration.contact.adapter.brevo.BrevoContactAdapter
import net.blueshell.api.platform.integration.contact.adapter.toContactData
import net.blueshell.api.platform.integration.contact.persistence.Contact
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactRepository
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.shared.enums.ContactSystem
import net.blueshell.api.shared.job.BrevoJobs
import net.blueshell.api.shared.job.ContactIntegrationJobProvider
import net.blueshell.api.shared.job.JobDefinition
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Handles create/update/delete of a Brevo contact for a single user.
 *
 * Reads entity state from [ContactRepository] to determine the operation:
 * - `deletedAt` set + external ID exists → delete from Brevo, clear external ID
 * - active + no external ID → create in Brevo, store external ID
 * - active + external ID exists → update in Brevo
 *
 * Also implements [ContactIntegrationJobProvider] so event listeners and
 * [SpawnContactSyncsJob] can dispatch Brevo-specific jobs without coupling.
 */
@Component
@Profile("!test & !dev")
class BrevoContactSyncJob(
    objectMapper: ObjectMapper,
    private val adapter: BrevoContactAdapter,
    private val contactRepository: ContactRepository,
    private val userService: UserService,
) : AbstractJsonJobHandler<BrevoJobs.BrevoContactSyncPayload>(
    objectMapper,
    BrevoJobs.SyncContact.payloadType,
), ContactIntegrationJobProvider {

    override val jobType: String = BrevoJobs.SyncContact.type
    override val system: ContactSystem = ContactSystem.BREVO

    override fun handlePayload(payload: BrevoJobs.BrevoContactSyncPayload) {
        val userId = payload.userId

        val contact = contactRepository.findByUserIdIncludingDeleted(userId)

        if (contact != null && contact.isSoftDeleted) {
            // Soft-deleted: remove from Brevo and clear stored external ID
            val externalId = contact.externalId(ContactSystem.BREVO)
            if (externalId == null) {
                log.debug("Contact for user {} is deleted but has no Brevo ID — nothing to delete", userId)
                return
            }
            adapter.deleteContact(externalId)
            contact.clearExternalId(ContactSystem.BREVO)
            contactRepository.save(contact)
            log.info("Deleted Brevo contact for user {}", userId)
            return
        }

        // Active contact: create or update
        val record = contact ?: contactRepository.save(Contact(userId = userId))
        val user = userService.findById(userId)
        val data = user.toContactData()

        val existingId = record.externalId(ContactSystem.BREVO)
        if (existingId == null) {
            val newId = adapter.createContact(data)
            record.setExternalId(ContactSystem.BREVO, newId)
            record.updateSnapshot(data.email, data.firstName, data.lastName, data.phoneNumber, data.newsletter, data.isMember)
            contactRepository.save(record)
            log.info("Created Brevo contact for user {}", userId)
        } else {
            adapter.updateContact(existingId, data)
            record.updateSnapshot(data.email, data.firstName, data.lastName, data.phoneNumber, data.newsletter, data.isMember)
            contactRepository.save(record)
            log.debug("Updated Brevo contact for user {}", userId)
        }
    }

    override fun contactSyncJob(userId: Long): Pair<JobDefinition<*>, Any> =
        Pair(BrevoJobs.SyncContact, BrevoJobs.BrevoContactSyncPayload(userId))

    override fun listSyncJob(userId: Long, contactListId: Long): Pair<JobDefinition<*>, Any> =
        Pair(BrevoJobs.SyncListMembership, BrevoJobs.BrevoListSyncPayload(userId, contactListId))

    companion object {
        private val log = LoggerFactory.getLogger(BrevoContactSyncJob::class.java)
    }
}
