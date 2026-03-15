package net.blueshell.api.platform.integration.contact.application.job

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.platform.integration.contact.adapter.ContactAdapter
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactRepository
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.SyncContactCommand
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Soft-deletes the local [Contact] record and dispatches per-integration contact sync jobs.
 *
 * Each per-integration sync job (Brevo/Listmonk) reads [Contact.deletedAt] via
 * [ContactRepository.findByUserIdIncludingDeleted] and removes the contact from the
 * external system, clearing the stored external ID.
 */
@Component
class DeleteContactJob(
    objectMapper: ObjectMapper,
    private val contactRepository: ContactRepository,
    private val contactAdapters: List<ContactAdapter>,
    private val jobs: TrackedJobDispatcher,
) : AbstractJsonJobHandler<ContactJobs.DeleteContactPayload>(objectMapper, ContactJobs.DeleteContact.payloadType) {
    override val jobType: String = ContactJobs.DeleteContact.type

    override fun handlePayload(payload: ContactJobs.DeleteContactPayload) {
        val userId = payload.userId

        // Soft-delete the Contact record so per-integration jobs can detect deletedAt.
        // Uses native SQL to avoid Hibernate cascading the delete to ContactExternalId records,
        // which the per-integration sync jobs still need to read.
        val contact = contactRepository.findByUserId(userId)
        if (contact != null) {
            contactRepository.softDeleteById(contact.id!!)
            log.info("Soft-deleted Contact record for user {}", userId)
        } else {
            log.debug("No Contact record for user {} — nothing to soft-delete", userId)
        }

        // Dispatch per-system sync jobs; each will read deletedAt and delete from external system
        contactAdapters.forEach { adapter ->
            runCatching {
                jobs.enqueue(ContactJobs.SyncContactToSystem, SyncContactCommand(userId, adapter.system))
            }.onFailure { e ->
                log.error("Failed to enqueue delete sync for user {} via {}: {}", userId, adapter.system, e.message)
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(DeleteContactJob::class.java)
    }
}
