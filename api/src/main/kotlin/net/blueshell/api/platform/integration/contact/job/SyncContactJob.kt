package net.blueshell.api.platform.integration.contact.job

import com.fasterxml.jackson.databind.ObjectMapper
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.application.contact.ContactData
import net.blueshell.api.domain.user.application.contact.ContactSyncAdapter
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.enums.Role
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

        // Create domain ContactData from User entity
        val contactData = ContactData(
            email = user.email,
            firstName = user.firstName,
            lastName = user.lastName,
            phoneNumber = user.phoneNumber,
            newsletter = user.newsletter,
            isMember = user.hasRole(Role.MEMBER)
        )

        // Sync contact and update user's contact ID if needed
        val contactId = contactAdapter.syncContact(user.id!!, contactData)
        if (user.contactId == null) {
            users.updateContactId(user.id!!, contactId.toLong())
        }
    }
}
