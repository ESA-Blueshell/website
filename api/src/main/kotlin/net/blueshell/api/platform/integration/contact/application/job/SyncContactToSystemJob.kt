package net.blueshell.api.platform.integration.contact.application.job

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.application.contact.ContactSyncAdapter
import net.blueshell.api.domain.user.application.contact.toContactData
import net.blueshell.api.platform.integration.contact.application.externalId
import net.blueshell.api.platform.integration.contact.application.setExternalId
import net.blueshell.api.platform.integration.contact.persistence.Contact
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactRepository
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.shared.job.ContactJobs
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Routes a create-or-update operation to the correct [ContactSyncAdapter] for a single system.
 *
 * Dispatched by [ContactSyncService] once per active adapter. Adapter routing is done via a
 * pre-built map keyed by [ContactSystem] — no when-switch in this handler.
 */
@Component
class SyncContactToSystemJob(
    objectMapper: ObjectMapper,
    adapters: List<ContactSyncAdapter>,
    private val contactRepository: ContactRepository,
    private val userService: UserService,
) : AbstractJsonJobHandler<ContactJobs.SyncContactToSystemPayload>(
    objectMapper,
    ContactJobs.SyncContactToSystem.payloadType
) {
    override val jobType: String = ContactJobs.SyncContactToSystem.type

    private val bySystem = adapters.associateBy { it.system }

    override fun handlePayload(payload: ContactJobs.SyncContactToSystemPayload) {
        val adapter = bySystem[payload.system]
        if (adapter == null) {
            log.warn("No adapter registered for system {} — skipping sync for user {}", payload.system, payload.userId)
            return
        }

        val record = contactRepository.findByUserId(payload.userId)
            ?: contactRepository.save(Contact(userId = payload.userId))

        val data = userService.findById(payload.userId).toContactData()
        val existingId = record.externalId(payload.system)

        if (existingId == null) {
            val newId = adapter.createContact(data)
            record.setExternalId(payload.system, newId)
            contactRepository.save(record)
        } else {
            adapter.updateContact(existingId, data)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SyncContactToSystemJob::class.java)
    }
}
