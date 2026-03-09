package net.blueshell.api.platform.integration.contact.application

import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Triggers a daily full-sync of all application users to external contact providers.
 *
 * Enqueues a single [ContactJobs.SpawnContactSyncs] job which performs the tracked,
 * retryable iteration over all users and integrations.
 */
@Component
class ContactSyncScheduler(
    private val jobs: TrackedJobDispatcher,
) {
    @Scheduled(cron = "\${contact.sync-cron:0 0 2 * * *}")
    fun syncAllContacts() {
        log.info("Scheduling contact sync spawn job")
        jobs.enqueue(ContactJobs.SpawnContactSyncs, ContactJobs.SpawnContactSyncsPayload())
    }

    companion object {
        private val log = LoggerFactory.getLogger(ContactSyncScheduler::class.java)
    }
}
