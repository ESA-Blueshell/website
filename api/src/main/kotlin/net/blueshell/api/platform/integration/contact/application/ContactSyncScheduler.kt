package net.blueshell.api.platform.integration.contact.application

import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Triggers daily full-syncs of all contacts and list memberships to external providers.
 *
 * Enqueues [ContactJobs.SpawnContactSyncs] and [ContactJobs.SpawnListMembershipSyncs] jobs
 * which perform the tracked, retryable iteration. The list sync runs 30 minutes after the
 * contact sync so contacts are likely already created when list membership is processed.
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

    @Scheduled(cron = "\${contact.list-sync-cron:0 30 2 * * *}")
    fun syncAllListMemberships() {
        log.info("Scheduling list membership sync spawn job")
        jobs.enqueue(ContactJobs.SpawnListMembershipSyncs, ContactJobs.SpawnListMembershipSyncsPayload())
    }

    companion object {
        private val log = LoggerFactory.getLogger(ContactSyncScheduler::class.java)
    }
}
