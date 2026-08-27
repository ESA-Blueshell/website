package net.blueshell.api.platform.integration.sync.application.job

import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.platform.integration.sync.application.ContactSyncService
import net.blueshell.api.shared.job.ContactJobs
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Soft-deletes a contact and pushes the removal to every registered
 * contact target. Enqueued by [ContactSyncListener] in response to
 * [UserDeleted] so the external delete is observable, retried, and
 * decoupled from the listener's own transaction.
 */
@Component
class RemoveContactJob(
    objectMapper: ObjectMapper,
    private val contactSync: ContactSyncService,
) : AbstractJsonJobHandler<ContactJobs.RemoveContactPayload>(
    objectMapper,
    ContactJobs.RemoveContact.payloadType,
) {
    override val jobType: String = ContactJobs.RemoveContact.type

    override fun handlePayload(payload: ContactJobs.RemoveContactPayload) {
        contactSync.remove(payload.userId)
    }
}
