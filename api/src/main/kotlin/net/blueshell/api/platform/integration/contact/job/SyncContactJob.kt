package net.blueshell.api.platform.integration.contact.job

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.application.contact.ContactSyncAdapter
import net.blueshell.api.domain.user.application.contact.toContactData
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.shared.job.ContactJobs
import org.springframework.stereotype.Component

/**
 * Job handler for syncing user contacts with external system.
 *
 * Uses ContactSyncAdapter (ADR-019 ACL) to isolate from specific contact provider.
 */
@Component
class SyncContactJob(
    objectMapper: ObjectMapper,
    private val contactAdapter: ContactSyncAdapter,
    private val users: UserService
) : AbstractJsonJobHandler<ContactJobs.SyncContactPayload>(objectMapper, ContactJobs.SyncContact.payloadType) {
    override val jobType: String = ContactJobs.SyncContact.type

    override fun handlePayload(payload: ContactJobs.SyncContactPayload) {
        val user = users.findById(payload.userId)

        val contactData = user.toContactData()

        // Sync contact and update user's contact ID if needed
        val contactId = contactAdapter.syncContact(user.id!!, user.contactId?.toString(), contactData)
        val syncedContactId = contactId.toLong()
        if (user.contactId != syncedContactId) {
            users.updateContactLink(user, syncedContactId)
        }
    }
}
