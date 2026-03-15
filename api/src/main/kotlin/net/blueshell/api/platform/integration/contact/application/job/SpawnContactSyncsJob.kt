package net.blueshell.api.platform.integration.contact.application.job

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.platform.integration.contact.adapter.ContactAdapter
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.SyncContactCommand
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Iterates all users and enqueues per-integration contact sync jobs for each.
 *
 * Replaces the inline loop in [ContactSyncScheduler]: the scheduler now simply
 * enqueues one [ContactJobs.SpawnContactSyncs] job, and this handler performs the
 * tracked, retryable iteration so that individual failures are visible in the job log.
 */
@Component
class SpawnContactSyncsJob(
    objectMapper: ObjectMapper,
    private val userService: UserService,
    private val contactAdapters: List<ContactAdapter>,
    private val jobs: TrackedJobDispatcher,
) : AbstractJsonJobHandler<ContactJobs.SpawnContactSyncsPayload>(
    objectMapper,
    ContactJobs.SpawnContactSyncs.payloadType,
) {
    override val jobType: String = ContactJobs.SpawnContactSyncs.type

    override fun handlePayload(payload: ContactJobs.SpawnContactSyncsPayload) {
        val users = userService.findAll()
        log.info("Spawning contact sync jobs for {} users × {} systems", users.size, contactAdapters.size)

        users.forEach { user ->
            contactAdapters.forEach { adapter ->
                runCatching {
                    jobs.enqueue(ContactJobs.SyncContactForSystem, SyncContactCommand(user.id!!, adapter.system))
                }.onFailure { e ->
                    log.error("Failed to enqueue contact sync for user {} via {}: {}", user.id, adapter.system, e.message)
                }
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SpawnContactSyncsJob::class.java)
    }
}
