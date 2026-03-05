package net.blueshell.api.platform.integration.contact.application

import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Schedules a daily full-sync of all application users to the external contact provider.
 *
 * Each user is enqueued as an individual [ContactJobs.SyncContact] job so that
 * syncs are tracked, retried on failure, and don't block the scheduler thread.
 *
 * The cron expression is configurable via `listmonk.contact.sync-cron` (default: 2 am daily).
 */
@Component
class ContactSyncScheduler(
    private val userService: UserService,
    private val jobs: TrackedJobDispatcher,
) {
    @Scheduled(cron = "\${listmonk.contact.sync-cron:0 0 2 * * *}")
    fun syncAllContacts() {
        val users = userService.findAll()
        log.info("Scheduling Listmonk contact sync for {} users", users.size)

        var enqueued = 0
        users.forEach { user ->
            try {
                jobs.enqueue(
                    ContactJobs.SyncContact,
                    ContactJobs.SyncContactPayload(userId = user.id!!),
                )
                enqueued++
            } catch (e: Exception) {
                log.error("Failed to enqueue contact sync for user id={}: {}", user.id, e.message)
            }
        }

        log.info("Enqueued {}/{} contact sync jobs", enqueued, users.size)
    }

    companion object {
        private val log = LoggerFactory.getLogger(ContactSyncScheduler::class.java)
    }
}
