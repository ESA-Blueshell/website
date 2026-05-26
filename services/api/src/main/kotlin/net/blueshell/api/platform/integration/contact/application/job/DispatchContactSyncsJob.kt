package net.blueshell.api.platform.integration.contact.application.job

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Daily bulk refresh: fan out one [ContactJobs.SyncContact] job per user.
 *
 * Each per-user job becomes its own JobExecution row with its own retry
 * schedule and observable failure state, so a single user's adapter failure
 * does not affect the rest of the batch and does not need an extra
 * REQUIRES_NEW transaction to isolate it. Matches the pattern already used
 * by [DispatchListMembershipSyncsJob].
 */
@Component
class DispatchContactSyncsJob(
    objectMapper: ObjectMapper,
    private val userService: UserService,
    private val jobs: TrackedJobDispatcher,
) : AbstractJsonJobHandler<ContactJobs.DispatchContactSyncsPayload>(
    objectMapper,
    ContactJobs.DispatchContactSyncs.payloadType,
) {
    override val jobType: String = ContactJobs.DispatchContactSyncs.type

    override fun handlePayload(payload: ContactJobs.DispatchContactSyncsPayload) {
        val users = userService.findAll()
        log.info("Enqueueing per-user contact sync jobs for {} users", users.size)
        users.forEach { user ->
            runCatching {
                jobs.enqueue(ContactJobs.SyncContact, ContactJobs.SyncContactPayload(user.id!!))
            }.onFailure { e ->
                log.error("Failed to enqueue contact sync for user {}: {}", user.id, e.message)
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(DispatchContactSyncsJob::class.java)
    }
}
