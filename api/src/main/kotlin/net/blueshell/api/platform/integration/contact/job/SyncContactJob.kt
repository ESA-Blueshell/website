package net.blueshell.api.platform.integration.contact.job

import com.fasterxml.jackson.databind.ObjectMapper
import net.blueshell.api.platform.integration.contact.ContactService
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.platform.integration.queue.ContactJobs
import net.blueshell.api.user.application.UserService
import org.springframework.stereotype.Component

@Component
class SyncContactJob(
    objectMapper: ObjectMapper,
    private val contacts: ContactService,
    private val users: UserService
) : AbstractJsonJobHandler<ContactJobs.SyncContactPayload>(objectMapper, ContactJobs.SyncContact.payloadType) {
    override val jobType: String = ContactJobs.SyncContact.type

    override fun handlePayload(payload: ContactJobs.SyncContactPayload) {
        val user = users.findById(payload.userId)
        contacts.sync(user)
    }
}
