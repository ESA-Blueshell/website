package net.blueshell.api.sync.domain

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.user.api.UserService
import net.blueshell.api.jobs.api.AbstractJsonJobHandler
import net.blueshell.api.shared.enums.Role
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
 * REQUIRES_NEW transaction to isolate it.
 */
@Component
class SyncAllContactsJob(
    objectMapper: ObjectMapper,
    private val userService: UserService,
    private val jobs: TrackedJobDispatcher,
) : AbstractJsonJobHandler<ContactJobs.SyncAllContactsPayload>(
    objectMapper,
    ContactJobs.SyncAllContacts.payloadType,
) {
    override val jobType: String = ContactJobs.SyncAllContacts.type

    override fun handlePayload(payload: ContactJobs.SyncAllContactsPayload) {
        // The service account is the site itself rather than somebody who reads mail. It owns
        // the files the repository ships with, and syncing it would put an address nobody
        // answers on the mailing list.
        val users = userService.findAll().filterNot { it.hasRole(Role.SYSTEM) }
        log.info("Enqueueing per-user contact sync jobs for {} users", users.size)
        users.forEach { user ->
            runCatching {
                jobs.runAsync(ContactJobs.SyncContact, ContactJobs.SyncContactPayload(user.id!!))
            }.onFailure { e ->
                log.error("Failed to enqueue contact sync for user {}: {}", user.id, e.message)
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SyncAllContactsJob::class.java)
    }
}
