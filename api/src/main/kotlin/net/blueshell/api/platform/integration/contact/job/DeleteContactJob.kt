package net.blueshell.api.platform.integration.contact.job

import com.fasterxml.jackson.databind.ObjectMapper
import net.blueshell.api.domain.user.application.contact.ContactSyncAdapter
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.shared.job.ContactJobs
import org.springframework.stereotype.Component

@Component
class DeleteContactJob(
    objectMapper: ObjectMapper,
    private val contactAdapter: ContactSyncAdapter
) : AbstractJsonJobHandler<ContactJobs.DeleteContactPayload>(objectMapper, ContactJobs.DeleteContact.payloadType) {
    override val jobType: String = ContactJobs.DeleteContact.type

    override fun handlePayload(payload: ContactJobs.DeleteContactPayload) {
        contactAdapter.deleteContact(payload.contactId.toString())
    }
}
