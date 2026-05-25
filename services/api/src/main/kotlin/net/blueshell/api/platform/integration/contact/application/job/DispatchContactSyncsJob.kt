package net.blueshell.api.platform.integration.contact.application.job

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.platform.integration.sync.application.ContactSyncService
import net.blueshell.api.shared.job.ContactJobs
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/** Daily bulk refresh of every user's contact state across all targets. */
@Component
class DispatchContactSyncsJob(
    objectMapper: ObjectMapper,
    private val userService: UserService,
    private val contactSync: ContactSyncService,
) : AbstractJsonJobHandler<ContactJobs.DispatchContactSyncsPayload>(
    objectMapper,
    ContactJobs.DispatchContactSyncs.payloadType,
) {
    override val jobType: String = ContactJobs.DispatchContactSyncs.type

    override fun handlePayload(payload: ContactJobs.DispatchContactSyncsPayload) {
        val users = userService.findAll()
        log.info("Refreshing contact sync for {} users", users.size)
        users.forEach { user ->
            runCatching { contactSync.sync(user.id!!) }.onFailure { e ->
                log.error("Bulk contact sync failed for user {}: {}", user.id, e.message)
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(DispatchContactSyncsJob::class.java)
    }
}
