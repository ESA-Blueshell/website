package net.blueshell.api.platform.integration.contact.application.job

import net.blueshell.api.platform.integration.contact.adapter.ContactAdapter
import net.blueshell.api.platform.integration.contact.adapter.ContactData
import net.blueshell.api.platform.integration.contact.persistence.Contact
import net.blueshell.api.platform.integration.contact.persistence.repository.ContactRepository
import net.blueshell.api.platform.integration.queue.ContactJobHandler
import net.blueshell.api.shared.enums.ContactSystem
import net.blueshell.api.shared.job.ContactJobs
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Routes a create-or-update operation to the correct [ContactSystemAdapter] for a single system.
 *
 * Dispatched by [ContactSyncService] once per active adapter. Data is read from the Contact
 * snapshot (synced_* fields), so no UserService lookup is needed at execution time.
 */
@Component
class SyncContactToSystemJob(
    objectMapper: ObjectMapper,
    adapters: List<ContactAdapter>,
    private val contactRepository: ContactRepository,
) : ContactJobHandler<ContactJobs.SyncContactToSystemPayload>(
    objectMapper,
    ContactJobs.SyncContactToSystem.payloadType,
    adapters,
) {
    override val jobType: String = ContactJobs.SyncContactToSystem.type

    override fun systemFrom(payload: ContactJobs.SyncContactToSystemPayload): ContactSystem = payload.system

    override fun handleForSystem(
        payload: ContactJobs.SyncContactToSystemPayload,
        adapter: ContactAdapter,
    ) {
        val record = contactRepository.findByUserId(payload.userId)
            ?: contactRepository.save(Contact(userId = payload.userId))

        val data = record.toContactData()
        val existingId = record.externalId(payload.system)

        if (existingId == null) {
            val newId = adapter.createContact(data)
            record.setExternalId(payload.system, newId)
            contactRepository.save(record)
        } else {
            adapter.updateContact(existingId, data)
        }
    }
}

private fun Contact.toContactData() = ContactData(
    email = syncedEmail,
    firstName = syncedFirstName,
    lastName = syncedLastName,
    phoneNumber = syncedPhoneNumber,
    newsletter = syncedNewsletter,
    isMember = syncedIsMember,
)
